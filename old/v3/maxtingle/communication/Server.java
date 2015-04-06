package com.maxtingle.communication;

import uk.co.maxtingle.communication.Debugger;
import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.common.events.MessageReceived;

import java.net.ServerSocket;
import java.util.ArrayList;

public class Server
{
    private int          _port;
    private ServerSocket _listener;
    private Thread       _clientListenerThread;
    private Thread       _messageListenerThread;
    private boolean _closing = false;

    private ArrayList<Client>          _clients;
    private ArrayList<MessageReceived> _messageReceivedListeners;

    public Server(int port) {
        this._port = port;
        this._clients = new ArrayList<Client>();
        this._messageReceivedListeners = new ArrayList<MessageReceived>();
    }

    public boolean isReady() {
        return !this._closing && this._listener != null && this._listener.isBound() && !this._listener.isClosed();
    }

    public ArrayList<Client> getClients() {
        return this._clients;
    }

    public void onMessageRecieved(MessageReceived listener) {
        this._messageReceivedListeners.add(listener);
    }

    public void start() throws Exception {
        this._listener = new ServerSocket(this._port);

        Debugger.log("Server", "Listening on " + this._listener.getInetAddress().toString() + ":" + this._port);
        this._listenForClients();
        this._listenForMessages();
    }

    public void stop() throws Exception {
        Debugger.log("Server", "Shutting down");
        this._closing = true;

        for (int i = this._clients.size() - 1; i > -1; i--) {
            this._clients.get(i).disconnect();
            this._clients.remove(i);
        }

        Debugger.log("Server", "Clients disconnected");

        if(this._clientListenerThread != null) {
            this._clientListenerThread.interrupt();
            this._clientListenerThread = null;
            Debugger.log("Server", "Client listener disabled");
        }

        if(this._messageListenerThread != null) {
            this._messageListenerThread.interrupt();
            this._messageListenerThread = null;
            Debugger.log("Server", "Message listener disabled");
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
                Debugger.log("Server", "Listening for clients");

                while (isReady()) {
                    try {
                        _clients.add(new Client(_listener.accept()));
                        Debugger.log("Server", "Accepted new client");
                    }
                    catch(Exception e) {
                        /*
                        * I tried to implement the better way of doing this I really did.
                        * It's caused by calling listener.close while the accept method is still blocking
                        * but there is no way to stop the accept method in its blocking state sooooo
                        * the only solution is to use nio (Non-blocking i/o) with channels.
                        * Then onto these channels (ServerSocketChannel and SocketChannel) you attach your
                        * selectors, selectors are essentially fancy event listeners that are meant to fire when
                        * certain events get through. Except the read and write even for when a SocketChannel has
                        * sent something never fired.
                        *
                        * Even if I had managed to get it to fire I would need a way
                        * to read all the bytes sent, but I have no way of knowing how many bytes have been sent,
                        * where the bytes end (There is no "end" of a message in TCP) and you have to allocate a buffer
                        * to read the bytes into so you may end up reading more bytes than you have space allocated
                        * for and that means BUFFER OVERFLOW YAY! That is unless you build some strange string
                        * builder to join multiple messages together, but all of that vs this one line and a little
                        * bit of my sanity lost, easy choice.
                        *
                        * So after spending a day doing effectively nothing, my conclusion was:
                        * Normally when you throw enough shit at the wall some of it will stick,
                        * in this case, when you throw enough shit at the wall you're just adding onto the shit Java
                        * already stuck there.
                        * */
                        if("socket closed".equals(e.getMessage())) {
                            return;
                        }

                        Debugger.log("Server", "Failed to accept client " + e.toString());
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
                Debugger.log("Server", "Listening for messages");

                while (isReady()) { //can't use this to reference because this now refers to the runnable, fucking java
                    try {
                        for (final Client client : _clients) {
                            if (client.isMessageWaiting()) {
                                Message message = client.getMessage();

                                if (message != null) {
                                    Debugger.log("Server", "Received message " + message.message);

                                    for(MessageReceived listener : _messageReceivedListeners) {
                                        listener.onMessageReceived(client, message);
                                    }
                                }
                                else {
                                    Debugger.log("Server", "Null message received");
                                }
                            }
                        }
                    }
                    catch(Exception e) {
                        Debugger.log("Server", "Error occurred while listening for messages " + e.toString());
                    }
                }
            }
        });
        this._messageListenerThread.start();
    }
}