package uk.co.maxtingle.communication.common.events;

import com.sun.istack.internal.NotNull;
import uk.co.maxtingle.communication.common.BaseClient;

/**
 * A delegate wrapper for when a client disconnects
 * from the server
 */
public interface DisconnectListener
{
    /**
     * The method to fire when a / the client has
     * disconnected from the server
     *
     * @param client The client that has disconnected
     */
    void onDisconnect(@NotNull BaseClient client);
}
