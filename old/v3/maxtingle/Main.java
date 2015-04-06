package com.maxtingle;

import com.google.gson.Gson;
import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.Debugger;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.server.Server;
import uk.co.maxtingle.communication.common.events.MessageReceived;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

interface TerminalListener {
    void onInput(String reply) throws Exception;
}

public class Main
{
    private static final String _address = "127.0.0.1";
    private static final int    _port    = 8080;

    private static BufferedReader   _terminalReader;
    private static TerminalListener _terminalOverride;

    public static final String newLineChar = "\n";
    public static Gson jsonParser;

    private static Client _client;
    private static Server _server;

    public static void main(String[] args) throws Exception {
        Main.jsonParser = new Gson();
        Main._terminalReader = new BufferedReader(new InputStreamReader(System.in));

        Debugger.setDefaultLogger();

        //server
        Main._server = new Server(Main._port);
        Main._server.onMessageReceived(new MessageReceived()
        {
            @Override
            public void onMessageReceived(final Client client, Message msg) throws Exception {
                Debugger.log("Server", "Enter a message to reply with");
                Main._terminalOverride = new TerminalListener()
                {
                    @Override
                    public void onInput(String reply) throws Exception {
                        client.sendMessage(new Message(reply));
                    }
                };
            }
        });
        Main._server.start();

        //client
        Main._client = new Client(new Socket(Main._address, Main._port));
        Main._client.listenForReplies();
        Debugger.log("Client", "Type something in to send it into the big wide world! Type quit to exit.");

        while(true) {
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