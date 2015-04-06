package com.maxtingle;

import uk.co.maxtingle.Main;
import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.common.Message;

import java.net.ServerSocket;
import java.util.ArrayList;

public class Server
{
    private int          _port;
    private ServerSocket _listener;
    private Thread       _clientListenerThread;
    private Thread       _messageListenerThread;
    private boolean _closing = false;

    private ArrayList<Client> _clients;

    public Server(int port) {
        this._port = port;
        this._clients = new ArrayList<Client>();
    }

    public boolean isReady() {
        return !this._closing && this._listener != null && this._listener.isBound() && !this._listener.isClosed();
    }

    public ArrayList<Client> getClients() {
        return this._clients;
    }

    public void start() throws Exception {
        this._listener = new ServerSocket(this._port);

        Main.debug("Server", "Listening on " + this._listener.getInetAddress().toString() + ":" + this._port);
        this._listenForClients();
        this._listenForMessages();
    }

    public void stop() throws Exception {
        Main.debug("Server", "Shutting down");
        this._closing = true;

        for (int i = this._clients.size() - 1; i > -1; i--) {
            this._clients.get(i).disconnect();
            this._clients.remove(i);
        }

        Main.debug("Server", "Clients disconnected");

        if(this._clientListenerThread != null) {
            this._clientListenerThread.interrupt();
            this._clientListenerThread = null;
            Main.debug("Server", "Client listener disabled");
        }

        if(this._messageListenerThread != null) {
            this._messageListenerThread.interrupt();
            this._messageListenerThread = null;
            Main.debug("Server", "Message listener disabled");
        }

        this._listener.close();
        this._listener = null;
        this._closing = false;
    }

    private void _listenForClients() throws Exception {
        if(this._clientListenerThread != null && this._clientListenerThread.isAlive()) {
            return; //already started
        }

        this._clientListenerThread = new Thread(new Runnable() //fuck everything about this exception handling
        {
            @Override
            public void run() {
                Main.debug("Server", "Listening for clients");

                while (isReady()) {
                    try {
                        _clients.add(new Client(_listener.accept()));
                        Main.debug("Server", "Accepted new client");
                    }
                    catch(Exception e) {
                        if("socket closed".equals(e.getMessage())) {
                            return; //exception caused by closing the listener while accept is still blocking.
                        }

                        Main.debug("Server", "Failed to accept client " + e.toString());
                    }
                }
            }
        });
        this._clientListenerThread.start();
    }

    private void _listenForMessages() {
        if(this._messageListenerThread != null && this._messageListenerThread.isAlive()) {
            return; //already started
        }

        this._messageListenerThread = new Thread(new Runnable()
        {//shall we implement delegates? Nah we've got inner classes, that's not at all like delegates, it's fine
            @Override
            public void run() {
                Main.debug("Server", "Listening for messages");

                while (isReady()) { //can't use this to reference because this now refers to the runnable, fucking java
                    try {
                        for (final Client client : _clients) {
                            if (client.isMessageWaiting()) {
                                Message message = client.getMessage();

                                if (message != null) {
                                    Main.debug("Server", "Received message " + message.message);
                                    Main.debug("Server", "Enter a message to reply with");
                                    Main.terminalOverride = new TerminalListener()
                                    {
                                        @Override
                                        public void onInput(String reply) throws Exception {
                                            client.sendMessage(new Message(reply));
                                        }
                                    };
                                }
                                else {
                                    Main.debug("Server", "Null message received");
                                }
                            }
                        }
                    }
                    catch(Exception e) {
                        Main.debug("Server", "Error occurred while listening for messages " + e.toString());
                    }
                }
            }
        });
        this._messageListenerThread.start();
    }
}