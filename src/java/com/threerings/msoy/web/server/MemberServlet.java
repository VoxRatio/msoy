//
// $Id$

package com.threerings.msoy.web.server;

import java.util.ArrayList;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

import com.threerings.msoy.server.MsoyServer;

import com.threerings.msoy.web.client.MemberService;
import com.threerings.msoy.web.data.MemberName;
import com.threerings.msoy.web.data.ServiceException;
import com.threerings.msoy.web.data.WebCreds;

import com.threerings.msoy.item.web.Item;
import com.threerings.msoy.world.data.MsoySceneModel;

import static com.threerings.msoy.Log.log;

/**
 * Provides the server implementation of {@link MemberService}.
 */
public class MemberServlet extends RemoteServiceServlet
    implements MemberService
{
    // from MemberService
    public MemberName getName (int memberId)
        throws ServiceException
    {
        ServletWaiter<MemberName> waiter =
            new ServletWaiter<MemberName>("getName[" + memberId + "]");
        MsoyServer.memberMan.getName(memberId, waiter);
        return waiter.waitForResult();
    }

    // from MemberService
    public boolean isOnline (int memberId)
        throws ServiceException
    {
        return MsoyServer.lookupMember(memberId) != null;
    }

    // from MemberService
    public Integer getMemberHomeId (WebCreds creds, final int memberId)
        throws ServiceException
    {
        final ServletWaiter<Integer> waiter =
            new ServletWaiter<Integer>("getHomeId[" + memberId + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.memberMan.getHomeId(MsoySceneModel.OWNER_TYPE_MEMBER, memberId, waiter);
            }
        });
        return waiter.waitForResult();
    }

    // from MemberService
    public void inviteFriend (final WebCreds creds, final int friendId)
        throws ServiceException
    {
        final ServletWaiter<Void> waiter =
            new ServletWaiter<Void>("inviteFriend[" + friendId + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.memberMan.alterFriend(creds.memberId, friendId, true, waiter);
            }
        });
        waiter.waitForResult();
    }

    // from MemberService
    public void acceptFriend (final WebCreds creds, final int friendId)
        throws ServiceException
    {
        final ServletWaiter<Void> waiter =
            new ServletWaiter<Void>("acceptFriend[" + friendId + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.memberMan.alterFriend(creds.memberId, friendId, true, waiter);
            }
        });
        waiter.waitForResult();
    }

    // from MemberService
    public void declineFriend (final WebCreds creds, final int friendId)
        throws ServiceException
    {
        final ServletWaiter<Void> waiter =
            new ServletWaiter<Void>("declineFriend[" + friendId + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.memberMan.alterFriend(creds.memberId, friendId, false, waiter);
            }
        });
        waiter.waitForResult();
    }

    // from interface MemberService
    public ArrayList loadInventory (final WebCreds creds, final byte type)
        throws ServiceException
    {
        // TODO: validate this user's creds

        // convert the string they supplied to an item enumeration
        if (Item.getClassForType(type) == null) {
            log.warning("Requested to load inventory for invalid item type " +
                        "[who=" + creds + ", type=" + type + "].");
            throw new ServiceException(ServiceException.INTERNAL_ERROR);
        }

        // load their inventory via the item manager
        final ServletWaiter<ArrayList<Item>> waiter = new ServletWaiter<ArrayList<Item>>(
            "loadInventory[" + creds.memberId + ", " + type + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.itemMan.loadInventory(creds.memberId, type, waiter);
            }
        });
        return waiter.waitForResult();
    }
    
    // from MemberService
    public String serializeNeighborhood (WebCreds creds, final int memberId, final boolean forGroup)
        throws ServiceException
    {
        final ServletWaiter<String> waiter =
            new ServletWaiter<String>("serializeNeighborhood[" + memberId + "]");
        MsoyServer.omgr.postRunnable(new Runnable() {
            public void run () {
                MsoyServer.memberMan.serializeNeighborhood(memberId, forGroup, waiter);
            }
        });
        return waiter.waitForResult();
    }

    // from MemberService
    public String serializePopularPlaces (WebCreds creds, int n)
        throws ServiceException
    {
        ServletWaiter<String> waiter =
            new ServletWaiter<String>("serializePopularPlaces[" + n + "]");
        MsoyServer.memberMan.serializePopularPlaces(n, waiter);
        return waiter.waitForResult();
    }
}
