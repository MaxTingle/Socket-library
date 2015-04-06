package uk.co.maxtingle;

import com.google.gson.Gson;
import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.Debugger;
import uk.co.maxtingle.communication.client.events.DisconnectListener;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.server.Server;
import uk.co.maxtingle.communication.common.events.MessageReceived;
import uk.co.maxtingle.communication.server.ServerOptions;
import uk.co.maxtingle.communication.server.events.AuthReceived;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

interface TerminalListener {
    void onInput(String reply) throws Exception;
}

public class Main
{
    private static final String _address  = "127.0.0.1";
    private static final int    _port     = 8080;
    private static final String _magic    = "testapplicationmagic";
    private static final String _username = "testuser";
    private static final String _password = "testpassword";

    private static BufferedReader   _terminalReader;
    private static TerminalListener _terminalOverride;

    public static Gson jsonParser;

    private static Client _client;
    private static Server _server;

    public static void main(String[] args) throws Exception {
        Main.jsonParser = new Gson();
        Main._terminalReader = new BufferedReader(new InputStreamReader(System.in));

        Debugger.setDefaultLogger();

        //server options
        ServerOptions options = new ServerOptions(); //stupid java no inline field value setting
        options.useMagic = true;
        options.useCredentials = true;
        options.expectedMagic = Main._magic;
        options.authHandler = new AuthReceived()
        {
            @Override
            public boolean checkAuth(String username, String password, Client client, Message message) throws Exception {
                return Main._username.equals(username) && Main._password.equals(password);
            }
        };

        //server
        Main._server = new Server(options);
        Main._server.onMessageReceived(new MessageReceived()
        {
            @Override
            public void onMessageReceived(final Client client, final Message msg) throws Exception {
                Debugger.log("Server", "Enter a message to reply with");
                Main._terminalOverride = new TerminalListener()
                {
                    @Override
                    public void onInput(String reply) throws Exception {
                        msg.respond(new Message(reply));
                    }
                };
            }
        });
        Main._server.start();

        //client
        Main._client = new Client(new Socket(Main._address, Main._port), Main._magic, Main._username, Main._password);
        Main._client.onDisconnect(new DisconnectListener()
        {
            @Override
            public void onDisconnect(Client client) {
                new Thread(new Runnable()
                {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(1000); //let the worker threads finish up their logging and such
                        }
                        catch (Exception e) {
                            System.out.println("Failed to wait, some error info maybe lost" + e.toString());
                        }
                        System.exit(0);
                    }
                }).start();
            }
        });
        Main._client.listenForReplies();
        Debugger.log("Client", "Type something in to send it into the big wide world! Type quit to exit.");

        //reading console input
        while(!Main._client.isStopped()) {
            String msg = Main._terminalReader.readLine();

            if("".equals(msg)) {
                Debugger.log("Client", "You can't send nothing!");
                continue;
            }
            else if("quit".equals(msg)) { //fuck everything about null msg
                Main._client.disconnect();
                Main._server.stop();
                break;
            }
            else if(Main._terminalOverride != null) {
                Main._terminalOverride.onInput(msg);
                Main._terminalOverride = null;
                continue;
            }

            Main._client.sendMessage(new Message(msg));
        }
    }
}