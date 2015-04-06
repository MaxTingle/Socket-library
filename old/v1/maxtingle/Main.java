package com.maxtingle;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

abstract class ReplyHandler {
    public abstract void onReply(String reply) throws Exception;
}

public class Main
{
    private static final String _newLineChar = "\n";
    private static final String _address = "127.0.0.1";
    private static final int    _port    = 8080;
    private static BufferedReader _terminalReader;

    private static ServerSocket      _listener;
    private static ReplyHandler _replyHandler;
    private static boolean _awaitingReply = false;

    private static Socket _socket;
    private static OutputStreamWriter _socketWriter;

    public static void main(String[] args) throws Exception {
        System.out.println("Type something in to send it into the big wide world!\n\nType quit to exit.");
        Main._terminalReader = new BufferedReader(new InputStreamReader(System.in));
        Main._startListening();
        Main._connectToListener();
        Main._listenForReplies();

        while(true) {
            String msg = Main._terminalReader.readLine();

            if(Main._awaitingReply) {
                Main._replyHandler.onReply(msg);
                continue;
            }

            if("".equals(msg)) {
                System.out.println("You can't send nothing!");
                continue;
            }
            else if("quit".equals(msg)) { //fuck everything about null msg
                Main._listener.close();
                break;
            }

            Main.sendMessage(msg);
        }
    }

    public static void sendMessage(String message) throws Exception {
        System.out.println("Sending " + message);
        Main._socketWriter.write(message + Main._newLineChar);
        Main._socketWriter.flush();
    }

    private static void _connectToListener() throws Exception {
        Main._socket = new Socket(Main._address, Main._port);
        Main._socketWriter = new OutputStreamWriter(Main._socket.getOutputStream());
        System.out.println("Connected to listener");
    }

    private static void _listenForReplies() throws Exception {
        final BufferedReader socketReader = new BufferedReader(new InputStreamReader(Main._socket.getInputStream()));

        new Thread(new Runnable()
        {
            @Override
            public void run() {
                try {
                    while (!Main._socket.isClosed()) {
                        System.out.println("Detected reply of " + socketReader.readLine());
                    }
                }
                catch(Exception e) {
                    System.out.println(e.toString());
                }
            }
        }).start();
    }

    private static void _startListening() throws Exception {
        Main._listener = new ServerSocket(Main._port);

        new Thread(new Runnable() //fuck everything about this exception handling
        {
            @Override
            public void run() {
                System.out.println("Listening on " + Main._address + ":" + Main._port);

                while (!Main._listener.isClosed()) {
                    try {
                        final Socket socket = Main._listener.accept(); //fuck everything about this bullshit closure
                        final OutputStreamWriter socketWriter = new OutputStreamWriter(socket.getOutputStream());

                        System.out.println("Attached new socket " + socket.getInetAddress().toString());

                        new Thread(new Runnable() //fuck everything about this stupid inner class closure / lambda / delegate wannabe
                        {
                            @Override
                            public void run() {
                                System.out.println("Listening for messages from attached socket");

                                try {
                                    BufferedReader socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                                    while(!socket.isClosed()) {
                                        //receiving
                                        System.out.println("Received " + socketReader.readLine());
                                        System.out.println("Type something in to reply");


                                        //replying
                                        Main._awaitingReply = true;
                                        Main._replyHandler = new ReplyHandler()
                                        {
                                            @Override
                                            public void onReply(String reply) throws Exception {
                                                System.out.println("Replying with " + reply);
                                                socketWriter.write(reply + Main._newLineChar);
                                                socketWriter.flush();
                                                Main._awaitingReply = false;
                                            }
                                        };
                                    }

                                    System.out.println("Stopped listening to socket " + socket.getInetAddress().toString());
                                }
                                catch(Exception e) {
                                    System.out.println(e.toString());
                                }
                            }
                        }).start();
                    }
                    catch(Exception e) {
                        System.out.println(e.toString());
                    }
                }
            }
        }).start();
    }
}