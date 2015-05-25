package uk.co.maxtingle.communication.common.events;

import com.sun.istack.internal.NotNull;
import uk.co.maxtingle.communication.common.BaseClient;
import uk.co.maxtingle.communication.common.Message;

/**
 * A delegate event wrapper for when the server / client
 * receives a message from a client / server
 */
public interface MessageReceived
{
    /**
     * The method / event handler to run when the server receives a message from
     * a client / the client receives a message from the server
     *
     * @param client The client that received / sent the message
     * @param msg    The message that was received
     */
    void onMessageReceived(@NotNull BaseClient client, @NotNull Message msg) throws Exception;
}