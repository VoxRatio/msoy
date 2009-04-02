//
// $Id$

package com.threerings.msoy.party.server;

import com.threerings.msoy.party.data.PartyMarshaller;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.server.InvocationDispatcher;
import com.threerings.presents.server.InvocationException;

/**
 * Dispatches requests to the {@link PartyProvider}.
 */
public class PartyDispatcher extends InvocationDispatcher<PartyMarshaller>
{
    /**
     * Creates a dispatcher that may be registered to dispatch invocation
     * service requests for the specified provider.
     */
    public PartyDispatcher (PartyProvider provider)
    {
        this.provider = provider;
    }

    @Override // documentation inherited
    public PartyMarshaller createMarshaller ()
    {
        return new PartyMarshaller();
    }

    @Override // documentation inherited
    public void dispatchRequest (
        ClientObject source, int methodId, Object[] args)
        throws InvocationException
    {
        switch (methodId) {
        case PartyMarshaller.ASSIGN_LEADER:
            ((PartyProvider)provider).assignLeader(
                source, ((Integer)args[0]).intValue(), (InvocationService.InvocationListener)args[1]
            );
            return;

        case PartyMarshaller.BOOT_MEMBER:
            ((PartyProvider)provider).bootMember(
                source, ((Integer)args[0]).intValue(), (InvocationService.InvocationListener)args[1]
            );
            return;

        case PartyMarshaller.INVITE_MEMBER:
            ((PartyProvider)provider).inviteMember(
                source, ((Integer)args[0]).intValue(), (InvocationService.InvocationListener)args[1]
            );
            return;

        case PartyMarshaller.MOVE_PARTY:
            ((PartyProvider)provider).moveParty(
                source, ((Integer)args[0]).intValue(), (InvocationService.InvocationListener)args[1]
            );
            return;

        case PartyMarshaller.UPDATE_RECRUITMENT:
            ((PartyProvider)provider).updateRecruitment(
                source, ((Byte)args[0]).byteValue(), (InvocationService.InvocationListener)args[1]
            );
            return;

        case PartyMarshaller.UPDATE_STATUS:
            ((PartyProvider)provider).updateStatus(
                source, (String)args[0], (InvocationService.InvocationListener)args[1]
            );
            return;

        default:
            super.dispatchRequest(source, methodId, args);
            return;
        }
    }
}
