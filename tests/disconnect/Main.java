package disconnect;

import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.common.BaseClient;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.debug.Debugger;
import uk.co.maxtingle.communication.server.Server;
import uk.co.maxtingle.communication.server.ServerOptions;

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
        Main._server = new Server(disconnect.Main._port);
        Main._server.start();

        //client
        BaseClient.logHeartbeat = true;
        ServerOptions.HEART_BMP = 15 * 1000;
        Main._client = new Client(new Socket(Main._address, Main._port));
        Main._client.sendMessage(new Message("yoyoyoyoy"));

        Main._silentClientDisconnect();
//        Main._silentServerDisconnect();

        while(!Main._client.isStopped()) {} //to stop app closing
    }

    private static void _silentClientDisconnect() {
        new Thread(new Runnable()
        {
            @Override
            public void run() {
                Debugger.log("Test", "Sleeping");
                try {
                    Thread.sleep(6000);
                    Debugger.log("Test", "Sleep done, shutting down client silently");
                    Main._client.disconnect();
                }
                catch(Exception e) {
                    Debugger.debug("Test", e);
                }
            }
        }).start();
    }

    private static void _silentServerDisconnect() {
        new Thread(new Runnable()
        {
            @Override
            public void run() {
                Debugger.log("Test", "Sleeping");
                try {
                    Thread.sleep(6000);
                    Debugger.log("Test", "Sleep done, shutting down server silently");
                    Main._server.stop();
                }
                catch(Exception e) {
                    Debugger.debug("Test", e);
                }
            }
        }).start();
    }
}