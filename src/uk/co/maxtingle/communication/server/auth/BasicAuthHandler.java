package uk.co.maxtingle.communication.server.auth;

import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.server.ServerClient;
import uk.co.maxtingle.communication.server.ServerOptions;

/**
 * An exceedingly basic auth handler that can be used
 * by the server for authentication
 */
public class BasicAuthHandler implements IAuthHandler
{
    /**
     * Checks whether the credentials passed
     * by a client are logic credentials
     *
     * @param username The username the client provided
     * @param password The password the client provided
     * @param client   The client attempting to authenticate
     * @param message  The message that the client sent to request
     *                 authentication of their credentials, you do not need
     *                 to reply to it manually, returning true will do it
     * @param options  The options currently being used by the server
     * @return Whether or not the credentials supplied are valid
     */
    @Override
    public boolean authCredentials(String username, String password, ServerClient client, Message message, ServerOptions options) throws Exception {
        return options.expectedUsername.equals(username) && options.expectedPassword.equals(password);
    }

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
    @Override
    public boolean authMagic(String magic, ServerClient client, Message message, ServerOptions options) throws Exception {
        return options.expectedMagic.equals(magic);
    }
}