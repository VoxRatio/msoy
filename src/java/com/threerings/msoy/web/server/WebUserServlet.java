//
// $Id$

package com.threerings.msoy.web.server;

import java.util.Calendar;
import java.util.Date;

import java.util.logging.Level;

import com.samskivert.io.PersistenceException;
import com.samskivert.net.MailUtil;
import com.samskivert.jdbc.DuplicateKeyException;

import com.threerings.msoy.data.MsoyAuthCodes;
import com.threerings.msoy.server.MsoyAuthenticator;
import com.threerings.msoy.server.MsoyServer;
import com.threerings.msoy.server.persist.MemberRecord;

import com.threerings.msoy.web.client.DeploymentConfig;
import com.threerings.msoy.web.client.WebUserService;
import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.web.data.ServiceException;
import com.threerings.msoy.web.data.WebCreds;
import com.threerings.msoy.web.data.Invitation;

import static com.threerings.msoy.Log.log;

/**
 * Provides the server implementation of {@link WebUserService}.
 */
public class WebUserServlet extends MsoyServiceServlet
    implements WebUserService
{
    // from interface WebUserService
    public WebCreds login (long clientVersion, String username, String password, int expireDays)
        throws ServiceException
    {
        checkClientVersion(clientVersion, username);
        // we are running on a servlet thread at this point and can thus talk to the authenticator
        // directly as it is thread safe (and it blocks) and we are allowed to block
        MsoyAuthenticator auth = (MsoyAuthenticator)MsoyServer.conmgr.getAuthenticator();
        return startSession(auth.authenticateSession(username, password), expireDays);
    }

    // from interface WebUserService
    public WebCreds register (long clientVersion, String username, String password, 
                              final String displayName, Date birthday, int expireDays, 
                              final Invitation invite)
        throws ServiceException
    {
        checkClientVersion(clientVersion, username);

        // check age restriction
        Calendar thirteenYearsAgo = Calendar.getInstance();
        thirteenYearsAgo.add(Calendar.YEAR, -13);
        if (birthday.compareTo(thirteenYearsAgo.getTime()) > 0) {
            throw new ServiceException(MsoyAuthCodes.SERVER_ERROR);
        }

        // check invitation validity
        boolean ignoreRestrict = false;
        if (invite != null) {
            try {
                if (MsoyServer.memberRepo.inviteAvailable(invite.inviteId)) {
                    ignoreRestrict = true;
                } else {
                    // this is likely due to an attempt to gain access through a trying random 
                    // invites.
                    throw new ServiceException(MsoyAuthCodes.SERVER_ERROR);
                }
            } catch (PersistenceException pe) {
                log.log(Level.WARNING, "checking invite available failed [inviteId=" +
                    invite.inviteId + "]", pe);
                throw new ServiceException(MsoyAuthCodes.SERVER_ERROR);
            }
        }

        // we are running on a servlet thread at this point and can thus talk to the authenticator
        // directly as it is thread safe (and it blocks) and we are allowed to block
        MsoyAuthenticator auth = (MsoyAuthenticator)MsoyServer.conmgr.getAuthenticator();
        final MemberRecord newAccount = auth.createAccount(username, password, displayName, 
            ignoreRestrict, invite != null ? invite.inviter.getMemberId() : 0);
        try {
            MsoyServer.profileRepo.setBirthday(newAccount.memberId, birthday);
        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "failed to set birthday on new account's profile [memberId=" +
                newAccount.memberId + ", birthday=" + birthday + "]", pe);
            throw new ServiceException(MsoyAuthCodes.SERVER_ERROR);
        }
        if (invite != null) {
            try {
                MsoyServer.memberRepo.linkInvite(invite, newAccount);
            } catch (PersistenceException pe) {
                log.log(Level.WARNING, "linking invites failed [inviteId=" + invite.inviteId + 
                    ", memberId=" + newAccount.memberId + "]", pe);
                throw new ServiceException(MsoyAuthCodes.SERVER_ERROR);
            }
            // send a notification email that the friend has accepted his invite
            final ServletWaiter<Void> waiter = new ServletWaiter<Void>(
                "deliver invite accepted message");
            MsoyServer.omgr.postRunnable(new Runnable() {
                public void run () {
                    // TODO How do we i18n this when we don't know anybody's locale??
                    MsoyServer.mailMan.deliverMessage(newAccount.memberId, 
                        invite.inviter.getMemberId(), "Invitation Accepted!",
                        "The invitation that you sent to " + invite.inviteeEmail + " has been " +
                        "accepted.  Your friend has chosen the display name \"" + displayName +
                        "\", and has been added to your friend's list.", null, waiter);
                }
            });
            waiter.waitForResult();
        }
        return startSession(newAccount, expireDays);
    }

    // from interface WebUserService
    public WebCreds validateSession (long clientVersion, String authtok, int expireDays)
        throws ServiceException
    {
        checkClientVersion(clientVersion, authtok);

        // refresh the token associated with their authentication session
        try {
            MemberRecord mrec = MsoyServer.memberRepo.refreshSession(authtok, expireDays);
            if (mrec == null) {
                return null;
            }

            WebCreds creds = mrec.toCreds(authtok);
            mapUser(creds, mrec);
            return creds;

        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Failed to refresh session [tok=" + authtok + "].", pe);
            throw new ServiceException(MsoyAuthCodes.SERVER_UNAVAILABLE);
        }
    }

    // from interface WebUserService
    public void updateEmail (WebCreds creds, String newEmail)
        throws ServiceException
    {
        MemberRecord mrec = requireAuthedUser(creds);

        if (!MailUtil.isValidAddress(newEmail)) {
            throw new ServiceException(MsoyAuthCodes.INVALID_EMAIL);
        }

        try {
            MsoyServer.memberRepo.configureAccountName(mrec.memberId, newEmail);
        } catch (DuplicateKeyException dke) {
            throw new ServiceException(MsoyAuthCodes.DUPLICATE_EMAIL);
        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Failed to set email [who=" + mrec.memberId +
                    ", email=" + newEmail + "].", pe);
            throw new ServiceException(ServiceException.INTERNAL_ERROR);
        }

        // let the authenticator know that we updated our account name
        MsoyAuthenticator auth = (MsoyAuthenticator)MsoyServer.conmgr.getAuthenticator();
        auth.updateAccount(mrec.accountName, newEmail, null, null);
    }

    // from interface WebUserService
    public void updatePassword (WebCreds creds, String newPassword)
        throws ServiceException
    {
        MemberRecord mrec = requireAuthedUser(creds);
        MsoyAuthenticator auth = (MsoyAuthenticator)MsoyServer.conmgr.getAuthenticator();
        auth.updateAccount(mrec.accountName, null, null, newPassword);
    }

    // from interface WebUserService
    public void configurePermaName (WebCreds creds, String permaName)
        throws ServiceException
    {
        MemberRecord mrec = requireAuthedUser(creds);
        if (mrec.permaName != null) {
            log.warning("Rejecting attempt to reassing permaname [who=" + mrec.accountName +
                        ", oname=" + mrec.permaName + ", nname=" + permaName + "].");
            throw new ServiceException(ServiceException.INTERNAL_ERROR);
        }

        if (permaName.length() < MemberName.MINIMUM_PERMANAME_LENGTH ||
            permaName.length() > MemberName.MAXIMUM_PERMANAME_LENGTH ||
            !permaName.matches(PERMANAME_REGEX)) {
            throw new ServiceException("e.invalid_permaname");
        }

        try {
            MsoyServer.memberRepo.configurePermaName(mrec.memberId, permaName);
        } catch (DuplicateKeyException dke) {
            throw new ServiceException(MsoyAuthCodes.DUPLICATE_PERMANAME);
        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Failed to set permaname [who=" + mrec.memberId +
                    ", pname=" + permaName + "].", pe);
            throw new ServiceException(ServiceException.INTERNAL_ERROR);
        }

        // let the authenticator know that we updated our permaname
        MsoyAuthenticator auth = (MsoyAuthenticator)MsoyServer.conmgr.getAuthenticator();
        auth.updateAccount(mrec.accountName, null, permaName, null);
    }

    protected void checkClientVersion (long clientVersion, String who)
        throws ServiceException
    {
        if (clientVersion != DeploymentConfig.version) {
            log.info("Refusing wrong version [who=" + who + ", cvers=" + clientVersion +
                     ", svers=" + DeploymentConfig.version + "].");
            throw new ServiceException(MsoyAuthCodes.VERSION_MISMATCH);
        }
    }

    protected WebCreds startSession (MemberRecord mrec, int expireDays)
        throws ServiceException
    {
        try {
            // if they made it through that gauntlet, create or update their session token
            WebCreds creds = mrec.toCreds(
                MsoyServer.memberRepo.startOrJoinSession(mrec.memberId, expireDays));
            mapUser(creds, mrec);
            return creds;

        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Failed to start session [for=" + mrec.accountName + "].", pe);
            throw new ServiceException(MsoyAuthCodes.SERVER_UNAVAILABLE);
        }
    }

    /** The regular expression defining valid permanames. */
    protected static final String PERMANAME_REGEX = "^[A-Za-z][_A-Za-z0-9]*$";
}
