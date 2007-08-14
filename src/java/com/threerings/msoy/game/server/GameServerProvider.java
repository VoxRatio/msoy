//
// $Id$

package com.threerings.msoy.game.server;

import com.threerings.msoy.game.client.GameServerService;
import com.threerings.msoy.game.data.GameSummary;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.server.InvocationProvider;

/**
 * Defines the server-side of the {@link GameServerService}.
 */
public interface GameServerProvider extends InvocationProvider
{
    /**
     * Handles a {@link GameServerService#clearGameHost} request.
     */
    public void clearGameHost (ClientObject caller, int arg1, int arg2);

    /**
     * Handles a {@link GameServerService#reportFlowAward} request.
     */
    public void reportFlowAward (ClientObject caller, int arg1, int arg2);

    /**
     * Handles a {@link GameServerService#sayHello} request.
     */
    public void sayHello (ClientObject caller, int arg1);

    /**
     * Handles a {@link GameServerService#updatePlayer} request.
     */
    public void updatePlayer (ClientObject caller, int arg1, GameSummary arg2);
}
