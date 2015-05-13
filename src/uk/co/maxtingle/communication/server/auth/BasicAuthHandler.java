package uk.co.maxtingle.communication.server.auth;

import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.server.ServerClient;
import uk.co.maxtingle.communication.server.ServerOptions;

public class BasicAuthHandler implements IAuthHandler
{
    @Override
    public boolean authCredentials(String username, String password, ServerClient client, Message message, ServerOptions options) throws Exception {
        return options.expectedUsername.equals(username) && options.expectedPassword.equals(password);
    }

    @Override
    public boolean authMagic(String magic, ServerClient client, Message message, ServerOptions options) throws Exception {
        return options.expectedMagic.equals(magic);
    }
}