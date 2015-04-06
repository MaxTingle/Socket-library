package uk.co.maxtingle.communication.server.events;

import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.common.Message;

public interface MagicReceived
{
    void onMagicReceived(boolean validMagic, Client client, Message msg) throws Exception;
}