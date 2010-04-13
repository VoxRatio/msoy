//
// $Id$

package com.threerings.msoy.world.tour.server;

import javax.annotation.Generated;

import com.threerings.msoy.world.tour.data.TourMarshaller;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.server.InvocationDispatcher;
import com.threerings.presents.server.InvocationException;

/**
 * Dispatches requests to the {@link TourProvider}.
 */
@Generated(value={"com.threerings.presents.tools.GenServiceTask"},
           comments="Derived from TourService.java.")
public class TourDispatcher extends InvocationDispatcher<TourMarshaller>
{
    /**
     * Creates a dispatcher that may be registered to dispatch invocation
     * service requests for the specified provider.
     */
    public TourDispatcher (TourProvider provider)
    {
        this.provider = provider;
    }

    @Override
    public TourMarshaller createMarshaller ()
    {
        return new TourMarshaller();
    }

    @Override
    public void dispatchRequest (
        ClientObject source, int methodId, Object[] args)
        throws InvocationException
    {
        switch (methodId) {
        case TourMarshaller.END_TOUR:
            ((TourProvider)provider).endTour(
                source
            );
            return;

        case TourMarshaller.NEXT_ROOM:
            ((TourProvider)provider).nextRoom(
                source, ((Boolean)args[0]).booleanValue(), (InvocationService.ResultListener)args[1]
            );
            return;

        default:
            super.dispatchRequest(source, methodId, args);
            return;
        }
    }
}
