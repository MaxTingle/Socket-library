package uk.co.maxtingle.communication.server.auth;

import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.server.ServerClient;
import uk.co.maxtingle.communication.server.ServerOptions;

/**
 * A handler for validating that a client
 * is the correct type so if you have multiple
 * servers and a client his one you can tell
 * if it was actually destined for this server or not
 *
 * If enabled this will always be the first stage in
 * client authentication
 */
public interface IMagicAuth
{
    /**
     * Validates that a client's magic is what the server
     * expects
     *
     * @param magic   The magic that the client sent
     * @param client  The client that wants magic authentication
     * @param message The message that the client sent to request
     *                authentication of their magic
     * @param options The options currently being used by the server
     * @return Whether or not the magic is correct
     */
    boolean authMagic(String magic, ServerClient client, Message message, ServerOptions options) throws Exception;
}
