package uk.co.maxtingle.communication.server.auth;

import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.server.ServerOptions;

public interface ICredentialAuth
{
    boolean authCredentials(String username, String password, Client client, Message message, ServerOptions options) throws Exception;
}
