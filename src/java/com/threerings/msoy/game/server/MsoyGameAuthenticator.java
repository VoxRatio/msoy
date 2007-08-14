//
// $Id$

package com.threerings.msoy.game.server;

import java.util.logging.Level;

import com.samskivert.io.PersistenceException;

import com.threerings.util.MessageBundle;
import com.threerings.util.Name;

import com.threerings.presents.net.AuthRequest;
import com.threerings.presents.net.AuthResponse;
import com.threerings.presents.net.AuthResponseData;
import com.threerings.presents.server.Authenticator;
import com.threerings.presents.server.net.AuthingConnection;

import com.threerings.msoy.data.MsoyAuthCodes;
import com.threerings.msoy.data.MsoyTokenRing;
import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.server.persist.MemberRecord;
import com.threerings.msoy.server.persist.MemberRepository;

import com.threerings.msoy.game.data.MsoyGameCredentials;
import com.threerings.msoy.web.client.DeploymentConfig;
import com.threerings.msoy.web.data.ServiceException;

import static com.threerings.msoy.Log.log;

/**
 * Handles authentication on an MSOY Game server.
 */
public class MsoyGameAuthenticator extends Authenticator
{
    public MsoyGameAuthenticator (MemberRepository memberRepo)
    {
        _memberRepo = memberRepo;
    }

    // from abstract Authenticator
    protected void processAuthentication (AuthingConnection conn, AuthResponse rsp)
        throws PersistenceException
    {
        AuthRequest req = conn.getAuthRequest();
        AuthResponseData rdata = rsp.getData();
        MsoyGameCredentials creds = null;

        try {
            // make sure they've got the correct version
            long cvers = 0L;
            long svers = DeploymentConfig.version;
            try {
                cvers = Long.parseLong(req.getVersion());
            } catch (Exception e) {
                // ignore it and fail below
            }
            if (svers != cvers) {
                log.info("Refusing wrong version [creds=" + req.getCredentials() +
                         ", cvers=" + cvers + ", svers=" + svers + "].");
                throw new ServiceException(
                    (cvers > svers) ? MsoyAuthCodes.NEWER_VERSION :
                    MessageBundle.tcompose(MsoyAuthCodes.VERSION_MISMATCH, svers));
            }

            // make sure they've sent valid credentials
            try {
                creds = (MsoyGameCredentials) req.getCredentials();
            } catch (ClassCastException cce) {
                log.log(Level.WARNING, "Invalid creds " + req.getCredentials() + ".", cce);
                throw new ServiceException(MsoyAuthCodes.SERVER_ERROR);
            }

            // if they're a guest, assign them a username and send them on their merry way
            if (creds.sessionToken == null) {
                // for guests, we use the same Name object as their username and their display
                // name. We create it here.
                creds.setUsername(
                    new MemberName(MsoyAuthCodes.GUEST_USERNAME_PREFIX + (++_guestCount),
                                   MemberName.GUEST_ID));
                rsp.authdata = null;
                rdata.code = AuthResponseData.SUCCESS;
                return;
            }

            MemberRecord member = _memberRepo.loadMemberForSession(creds.sessionToken);
            if (member == null) {
                throw new ServiceException(MsoyAuthCodes.SESSION_EXPIRED);
            }

            // set their starting username to their auth username
            creds.setUsername(new Name(member.accountName));

            // replace the tokens provided by the Domain with tokens derived from their member
            // record (a newly created record will have its bits set from the Domain values)
            int tokens = 0;
            if (member.isSet(MemberRecord.ADMIN_FLAG)) {
                tokens |= MsoyTokenRing.ADMIN;
                tokens |= MsoyTokenRing.SUPPORT;
            } else if (member.isSet(MemberRecord.SUPPORT_FLAG)) {
                tokens |= MsoyTokenRing.SUPPORT;
            }
            rsp.authdata = new MsoyTokenRing(tokens);

            // log.info("User logged on [user=" + user.username + "].");
            rdata.code = AuthResponseData.SUCCESS;

        } catch (ServiceException se) {
            rdata.code = se.getMessage();
            log.info("Rejecting authentication [creds=" + creds + ", code=" + rdata.code + "].");
        }
    }

    protected MemberRepository _memberRepo;

    /** Used to assign unique usernames to guests that authenticate with the server. */
    protected static int _guestCount;
}
