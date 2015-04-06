package uk.co.maxtingle.communication.client.events;

import uk.co.maxtingle.communication.client.Client;

public interface DisconnectListener
{
    void onDisconnect(Client client);
}
