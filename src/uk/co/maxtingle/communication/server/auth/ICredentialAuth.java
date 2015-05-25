package uk.co.maxtingle.communication.server.auth;

import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.server.ServerClient;
import uk.co.maxtingle.communication.server.ServerOptions;

/**
 * A handle for validating a clients
 * credentials when they are trying to
 * authenticate with the server. If magic
 * auth is used, this will be the second
 * stage in authentication
 */
public interface ICredentialAuth
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
    boolean authCredentials(String username, String password, ServerClient client, Message message, ServerOptions options) throws Exception;
}
