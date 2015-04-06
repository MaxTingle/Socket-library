package uk.co.maxtingle.communication.common.events;

import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.common.Message;

public interface MessageReceived
{
    void onMessageReceived(Client client, Message msg) throws Exception;
}