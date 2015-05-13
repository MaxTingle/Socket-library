package uk.co.maxtingle.communication.common.events;

import uk.co.maxtingle.communication.common.BaseClient;

public interface DisconnectListener
{
    void onDisconnect(BaseClient client);
}
