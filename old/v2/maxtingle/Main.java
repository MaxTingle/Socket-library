package com.maxtingle;

import com.google.gson.Gson;
import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.server.Server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

abstract class TerminalListener
{
    public abstract void onInput(String reply) throws Exception;
}

public class Main
{
    private static final String _address = "127.0.0.1";
    private static final int    _port    = 8080;

    private static BufferedReader   _terminalReader;
    public static  TerminalListener terminalOverride;

    public static final String newLineChar = "\n";
    public static Gson jsonParser;

    private static Client _client;
    private static Server _server;

    public static void main(String[] args) throws Exception {
        Main.jsonParser = new Gson();
        Main._terminalReader = new BufferedReader(new InputStreamReader(System.in));

        //server
        Main._server = new Server(Main._port);
        Main._server.start();

        //client
        Main._client = new Client(new Socket(Main._address, Main._port));
        Main._client.listenForReplies();
        Main.debug("Client", "Type something in to send it into the big wide world! Type quit to exit.");

        while(true) {
            String msg = Main._terminalReader.readLine();

            if(Main.terminalOverride != null) {
                Main.terminalOverride.onInput(msg);
                Main.terminalOverride = null;
                continue;
            }

            if("".equals(msg)) {
                Main.debug("Client", "You can't send nothing!");
                continue;
            }
            else if("quit".equals(msg)) { //fuck everything about null msg
                Main._client.disconnect();
                Main._server.stop();
                break;
            }

            Main._client.sendMessage(new Message(msg));
        }
    }

    public static void debug(String source, String msg) {
        System.out.println("[" + source + "] " + msg);
    }
}