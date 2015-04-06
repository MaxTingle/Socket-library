package uk.co.maxtingle.communication.server.events;

import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.common.Message;

public interface AuthReceived
{
    boolean checkAuth(String username, String password, Client client, Message message) throws Exception;
}
