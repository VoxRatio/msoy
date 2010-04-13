//
// $Id$

package com.threerings.msoy.item.server;

import javax.annotation.Generated;

import com.threerings.msoy.item.data.ItemMarshaller;
import com.threerings.msoy.item.data.all.ItemFlag;
import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.server.InvocationDispatcher;
import com.threerings.presents.server.InvocationException;

/**
 * Dispatches requests to the {@link ItemProvider}.
 */
@Generated(value={"com.threerings.presents.tools.GenServiceTask"},
           comments="Derived from ItemService.java.")
public class ItemDispatcher extends InvocationDispatcher<ItemMarshaller>
{
    /**
     * Creates a dispatcher that may be registered to dispatch invocation
     * service requests for the specified provider.
     */
    public ItemDispatcher (ItemProvider provider)
    {
        this.provider = provider;
    }

    @Override
    public ItemMarshaller createMarshaller ()
    {
        return new ItemMarshaller();
    }

    @Override
    public void dispatchRequest (
        ClientObject source, int methodId, Object[] args)
        throws InvocationException
    {
        switch (methodId) {
        case ItemMarshaller.ADD_FLAG:
            ((ItemProvider)provider).addFlag(
                source, (ItemIdent)args[0], (ItemFlag.Kind)args[1], (String)args[2], (InvocationService.ConfirmListener)args[3]
            );
            return;

        case ItemMarshaller.DELETE_ITEM:
            ((ItemProvider)provider).deleteItem(
                source, (ItemIdent)args[0], (InvocationService.ConfirmListener)args[1]
            );
            return;

        case ItemMarshaller.GET_CATALOG_ID:
            ((ItemProvider)provider).getCatalogId(
                source, (ItemIdent)args[0], (InvocationService.ResultListener)args[1]
            );
            return;

        case ItemMarshaller.GET_ITEM_NAMES:
            ((ItemProvider)provider).getItemNames(
                source, (ItemIdent[])args[0], (InvocationService.ResultListener)args[1]
            );
            return;

        case ItemMarshaller.PEEP_ITEM:
            ((ItemProvider)provider).peepItem(
                source, (ItemIdent)args[0], (InvocationService.ResultListener)args[1]
            );
            return;

        case ItemMarshaller.RECLAIM_ITEM:
            ((ItemProvider)provider).reclaimItem(
                source, (ItemIdent)args[0], (InvocationService.ConfirmListener)args[1]
            );
            return;

        default:
            super.dispatchRequest(source, methodId, args);
            return;
        }
    }
}
