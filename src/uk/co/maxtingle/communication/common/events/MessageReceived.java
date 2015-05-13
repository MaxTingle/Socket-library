package uk.co.maxtingle.communication.common.events;

import uk.co.maxtingle.communication.common.BaseClient;
import uk.co.maxtingle.communication.common.Message;

public interface MessageReceived
{
    void onMessageReceived(BaseClient client, Message msg) throws Exception;
}