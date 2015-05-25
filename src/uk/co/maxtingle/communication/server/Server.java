package uk.co.maxtingle.communication.server;

import com.sun.istack.internal.NotNull;
import uk.co.maxtingle.communication.common.AuthState;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.common.events.AuthStateChanged;
import uk.co.maxtingle.communication.common.events.DisconnectListener;
import uk.co.maxtingle.communication.common.events.MessageReceived;
import uk.co.maxtingle.communication.common.exception.AuthException;
import uk.co.maxtingle.communication.common.exception.InvalidMessageException;
import uk.co.maxtingle.communication.debug.Debugger;
import uk.co.maxtingle.communication.server.auth.BasicAuthHandler;
import uk.co.maxtingle.communication.server.auth.IAuthHandler;
import uk.co.maxtingle.communication.server.auth.ICredentialAuth;
import uk.co.maxtingle.communication.server.auth.IMagicAuth;

import java.net.ServerSocket;
import java.util.ArrayList;

public class Server
{
    protected ServerOptions _options;
    protected ArrayList<ServerClient>       _clients                  = new ArrayList<ServerClient>();
    protected ArrayList<MessageReceived>    _messageReceivedListeners = new ArrayList<MessageReceived>();
    ArrayList<AuthStateChanged>   _authStateChangedListeners = new ArrayList<AuthStateChanged>();
    ArrayList<DisconnectListener> _disconnectListeners = new ArrayList<DisconnectListener>();

    private ServerSocket  _listener;
    private Thread        _clientListenerThread;
    private Thread        _messageListenerThread;
    private Thread        _heartThread;
    private boolean _closing = false;

    /**
     * Creates a new server and server the options to the default
     * ServerOptions values. Then sets the default authentication handler
     */
    public Server() {
        this._options = new ServerOptions() {};
        this.setDefaultAuthHandler();
    }

    /**
     * Creates a new server and server the server options to the
     * default ServerOptions values but overrides the port to
     * the port specified. Then sets the default authentication handler
     *
     * @param serverPort The port to listen on
     */
    public Server(@NotNull int serverPort) {
        this._options = new ServerOptions();
        this._options.port = serverPort;
        this.setDefaultAuthHandler();
    }

    /**
     * Creates a new server with the specified ServerOptions
     * and checks that they are valid then sets the default
     * authentication handler
     *
     * @param options The options to configure the server with
     * @throws Exception Invalid options values
     */
    public Server(ServerOptions options) throws Exception {
        if (options.useMagic && (options.expectedMagic == null || options.expectedMagic.trim().equals(""))) {
            throw new Exception("Cannot use expectedMagic without setting expected expectedMagic value");
        }

        this._options = options;
        this.setDefaultAuthHandler();
    }

    /**
     * Checks whether or not the server is ready,
     * not shutdown and has a bound listener
     *
     * @return Whether or not the server is operational
     */
    public boolean isReady() {
        return !this._closing && this._listener != null && this._listener.isBound() && !this._listener.isClosed();
    }

    /**
     * Gets all the clients currently connected to the server.
     * Take note that some of the clients could be in between
     * heartbeats but disconnected so IO Operations might
     * fail on them
     *
     * @return The clients connected to the server
     */
    public ArrayList<ServerClient> getClients() {
        return this._clients;
    }

    /**
     * Adds a MessageReceived listener to the list
     * of events to fire when the server receives a message
     * from a client
     *
     * @param listener The listener to add
     */
    public void onMessageReceived(@NotNull MessageReceived listener) {
        this._messageReceivedListeners.add(listener);
    }

    /**
     * Adds an AuthStateChanged listener to the list
     * of events to fire when the server changes the auth
     * state of a client
     *
     * @param listener The listener to add
     */
    public void onClientAuthStateChanged(@NotNull AuthStateChanged listener) {
        this._authStateChangedListeners.add(listener);
    }

    /**
     * Adds a DisconnectListener to the list of events to fire
     * when the server disconnects a client or a heartbeat
     * fails and the client disconnects themselves
     *
     * @param listener The listener to add
     */
    public void onClientDisconnect(DisconnectListener listener) {
        this._disconnectListeners.add(listener);
    }

    /**
     * Sets the auth handler to a new instance of the
     * BasicAuthHandler
     */
    public void setDefaultAuthHandler() {
        this.setAuthHandler(new BasicAuthHandler());
    }

    /**
     * Sets the auth handler to use for all authentication
     * to the server, this means magic and credential based
     * authentication, whether or not the methods will be used
     * is down to the ServerOptions
     *
     * @param handler The handler to use
     */
    public void setAuthHandler(@NotNull final IAuthHandler handler) {
        this._options.usedAuthHandler = handler;
        this._options.magicAuthHandler = new IMagicAuth()
        {
            @Override
            public boolean authMagic(String magic, ServerClient client, Message message, ServerOptions options) throws Exception {
                return handler.authMagic(magic, client, message, options);
            }
        };

        this._options.credentialAuthHandler = new ICredentialAuth()
        {
            @Override
            public boolean authCredentials(String username, String password, ServerClient client, Message message, ServerOptions options) throws Exception {
                return handler.authCredentials(username, password, client, message, options);
            }
        };
    }

    /**
     * Sets the magic authentication handler to use when a client
     * attempts to connect to the server, whether or not this is
     * used is determined by the ServerOptions
     *
     * @param handler The handler to use
     */
    public void setMagicAuthHandler(@NotNull final IMagicAuth handler) {
        this._options.magicAuthHandler = handler;
    }

    /**
     * Sets the credential authentication handler to use when a client
     * attempts to connect to the server and has verified their magic
     * if the server is using it, whether or not this is used is
     * determined by the ServerOptions
     *
     * @param handler The handler to use
     */
    public void setCredentialAuthHandler(final ICredentialAuth handler) {
        this._options.credentialAuthHandler = handler;
    }

    /**
     * Gets all the clients which have been authenticated and set
     * to the state ACCEPTED
     *
     * @return The clients which have been authenticated
     */
    public ArrayList<ServerClient> getAcceptedClients() {
        return this.getClientsInState(AuthState.ACCEPTED);
    }

    /**
     * Gets all the clients which are in a certain state
     *
     * @param state The state to find clients that are in
     * @return The clients in the state given
     */
    public ArrayList<ServerClient> getClientsInState(@NotNull AuthState state) {
        ArrayList<ServerClient> matches = new ArrayList<ServerClient>();

        for(ServerClient client : this._clients) {
            if(client.getAuthState() == state) {
                matches.add(client);
            }
        }

        return matches;
    }

    /**
     * Starts the server socket, binds the server listener,
     * starts listening for messages from clients,
     * starts listening for new clients attempting to connect
     * and starts the heartbeat thread to make sure clients
     * are still connected when they say they are.
     *
     * @throws Exception Failed to start the server
     */
    public void start() throws Exception {
        this._listener = new ServerSocket(this._options.port);

        Debugger.log("Server", "Listening on " + this._listener.getInetAddress().toString() + ":" + this._options.port);
        this._listenForClients();
        this._listenForMessages();
        this._startHeart();
    }

    /**
     * Gracefully stops the server by allowing
     * threads to stop naturally and then
     * interrupting them if they are sleeping
     * but ignoring the error. First stops
     * listener for more clients, then stops sending
     * heart beat requests, then stops listening for messages
     * and finally disconnects all clients which will cause
     * the disconnect listener to fire for each client
     *
     * @throws Exception A client failed to disconnect or interrupting a thread failed
     */
    public void stop() throws Exception {
        Debugger.log("Server", "Shutting down");
        this._closing = true;

        if(this._clientListenerThread != null) {
            this._clientListenerThread.interrupt();
            this._clientListenerThread = null;
            Debugger.log("Server", "Client listener disabled");
        }

        if(this._heartThread != null) {
            this._heartThread.interrupt();
            this._heartThread = null;
            Debugger.log("Server", "Heartbeat detector disabled");
        }

        if(this._messageListenerThread != null) {
            this._messageListenerThread.interrupt();
            this._messageListenerThread = null;
            Debugger.log("Server", "Message listener disabled");
        }

        for (int i = this._clients.size() - 1; i > -1; i--) {
            this._clients.get(i).disconnect(); //on disconnect event will remove it from array
        }

        Debugger.log("Server", "Clients disconnected");

        this._listener.close();
        this._listener = null;
        this._closing = false;
    }

    protected void _listenForClients() throws Exception {
        if(this._clientListenerThread != null && this._clientListenerThread.isAlive()) {
            return; //already started
        }

        this._clientListenerThread = new Thread(new Runnable()
        {
            @Override
            public void run() {
                Debugger.log("Server", "Listening for clients");

                while (Server.this.isReady()) {
                    try {
                        ServerClient client = new ServerClient(Server.this._listener.accept(), Server.this);
                        Server.this._clients.add(client);
                        Debugger.log("Server", "Accepted new client - " + client.getSocket().getInetAddress().toString());
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
        this._clientListenerThread.setName("Client listener");
        this._clientListenerThread.start();
    }

    protected void _startHeart() {
        if(this._heartThread != null && this._heartThread.isAlive()) {
            return;
        }

        this._heartThread = new Thread(new Runnable()
        {
            @Override
            public void run() {
                try {
                    Thread.sleep(ServerOptions.HEART_BMP); //will have only just connected, no need to beat the heart straight away
                }
                catch(Exception e) {
                    if(!Server.this._closing) {
                        Debugger.log("Server", "Failed to skip first beat " + e.getMessage());
                    }
                }

                while(Server.this.isReady()) {
                    for(int i = _clients.size() - 1; i != -1; i--) { //will be dynamically removing from list
                        ServerClient client = _clients.get(i);
                        try {
                            client.sendMessage(new Message(ServerOptions.HEART_BEAT));
                        }
                        catch(Exception e) {
                            if(!Server.this._closing) {
                                Debugger.log("Server", "Heart beat failed, client died: " + e.getMessage());
                                client.disconnect();
                            }
                        }
                    }

                    try {
                        Thread.sleep(ServerOptions.HEART_BMP);
                    }
                    catch(InterruptedException e) {
                        Debugger.log("Server", "Error sleeping heart thread " + e.getMessage());
                    }
                }
            }
        });
        this._heartThread.setName("Server heart");
        this._heartThread.start();
    }

    protected void _listenForMessages() {
        if(this._messageListenerThread != null && this._messageListenerThread.isAlive()) {
            return; //already started
        }

        this._messageListenerThread = new Thread(new Runnable()
        {
            @Override
            public void run() {
                Debugger.log("Server", "Listening for messages");

                while (isReady()) {
                    try {
                        for(int i = Server.this._clients.size() - 1; i != -1; i--) { //will be dynamically removing from list
                            ServerClient client = Server.this._clients.get(i);

                            if(client == null) {
                                continue;
                            }
                            else if (client.isStopped()) {
                                continue;
                            }
                            else if (!client.isMessageWaiting()) {
                                continue;
                            }

                            Message message = null;
                            try {
                                message = client.getMessage();

                                if (ServerOptions.HEART_BEAT.equals(message.request)) {
                                    continue; //just a heart beat message, take no note
                                }
                                else if (client.getAuthState() != AuthState.ACCEPTED) {
                                    if (ServerOptions.SEND_MAGIC.equals(message.request) || ServerOptions.SEND_CREDENTIALS.equals(message.request)) {
                                        client.handleAuthMessage(message);
                                    }
                                    else { //asked for expectedMagic / credentials, didn't get it
                                        message.respond(new Message(false, "Not authenticated, request rejected"));
                                        client.disconnect();
                                    }
                                }
                                else {
                                    /* Server handling the message */
                                    for (MessageReceived listener : _messageReceivedListeners) {
                                        listener.onMessageReceived(client, message);
                                    }

                                    /* Client specific server side bounds handling the message, things like onReply will trigger from this */
                                    client.handleMessage(message);
                                }
                            }
                            catch (InvalidMessageException e) {
                                Debugger.log("Server", "Client sent invalid message (" + e.getMessage() + "), disconnecting.");
                                client.disconnect();
                            }
                            catch(AuthException e) {
                                if(message != null) {
                                    message.respond(new Message(false, e.getMessage()));
                                }
                                client.disconnect();
                            }
                            catch (Exception e) { //failed auth
                                if(message != null) {
                                    message.respond(new Message(false, e.toString()));
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
        this._messageListenerThread.setName("Message listener");
        this._messageListenerThread.start();
    }
}