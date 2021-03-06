package replies;

import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.common.AuthState;
import uk.co.maxtingle.communication.common.BaseClient;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.common.events.AuthStateChanged;
import uk.co.maxtingle.communication.common.events.MessageReceived;
import uk.co.maxtingle.communication.debug.Debugger;
import uk.co.maxtingle.communication.server.Server;

import java.net.Socket;

public class Main
{
    private static final String _address = "127.0.0.1";
    private static final int    _port    = 8080;

    private static BaseClient _client;
    private static Server     _server;

    public static void main(String[] args) throws Exception {
        Debugger.setDefaultLogger();

        //server
        Main._server = new Server(Main._port);
        Main._server.onMessageReceived(new MessageReceived()
        {
            @Override
            public void onMessageReceived(final BaseClient baseClient, final Message msg) throws Exception {
                msg.respond(new Message("yoyoyoyoyoyo"));
            }
        });
        Main._server.start();

        //client
        Main._client = new Client();
        Main._client.onAuthStateChange(new AuthStateChanged()
        {
            @Override
            public void onAuthStateChanged(AuthState previous, AuthState newState, BaseClient baseClient) {
                if (newState == AuthState.ACCEPTED) {
                    //just so once the client is ready we can do the message
                    Message message = new Message("Send me a reply!");
                    message.onReply(new MessageReceived()
                    {
                        @Override
                        public void onMessageReceived(BaseClient baseClient, Message msg) throws Exception {
                            Debugger.log("Client", "I got my response! it was " + msg.request + " in response to my " + msg.getResponseTo().request);
                        }
                    });

                    try {
                        Main._client.sendMessage(message);
                    }
                    catch (Exception e) {
                        Debugger.log("Client", e);
                    }
                }
            }
        });
        Main._client.connect(new Socket(Main._address, Main._port));

        while(!Main._client.isStopped()) {} //to stop app closing
    }
}