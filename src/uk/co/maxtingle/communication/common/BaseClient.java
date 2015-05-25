package uk.co.maxtingle.communication.common;

import com.google.gson.JsonSyntaxException;
import com.sun.istack.internal.NotNull;
import uk.co.maxtingle.communication.common.events.AuthStateChanged;
import uk.co.maxtingle.communication.common.events.DisconnectListener;
import uk.co.maxtingle.communication.common.events.MessageReceived;
import uk.co.maxtingle.communication.common.exception.InvalidMessageException;
import uk.co.maxtingle.communication.debug.Debugger;
import uk.co.maxtingle.communication.server.ServerOptions;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * A base class for the Client that connects
 * to the server and the server's representation of
 * a client that is connected to it. When you see
 * client / server worded docblocks then what
 * type of client it is determines which
 * one you should pay attention too
 */
public abstract class BaseClient
{
    protected Socket             _socket;
    protected OutputStreamWriter _writer;
    protected InputStream        _inputStream;
    protected BufferedReader     _reader;

    protected boolean _closed = false;

    /* Client event handlers and tracking */
    protected ArrayList<DisconnectListener> _disconnectListeners      = new ArrayList<DisconnectListener>();
    protected ArrayList<AuthStateChanged>   _authStateListeners       = new ArrayList<AuthStateChanged>();
    protected ArrayList<MessageReceived>    _messageReceivedListeners = new ArrayList<MessageReceived>();
    protected HashMap<String, Message>      _receivedMessages         = new HashMap<String, Message>();
    protected HashMap<String, Message>      _sentMessages             = new HashMap<String, Message>();
    protected AuthState                     _authState                = AuthState.CONNECTED;

    /* Client settings */
    /**
     * Whether or not to use Debugger.log on messages
     * that are purely heartbeat checks
     */
    public static boolean logHeartbeat = false;

    /**
     * Whether or not to keep messages in the
     * internal list of sent messages, useful for
     * getting messages later on and a requirement
     * for detecting what an incoming message is a reply
     * to as the id of the previous message is required
     */
    public transient boolean keepMessages = true; //can disable this for memory usage but doing so will mean manual replies needed

    /**
     * Associates a socket with the client and
     * sets all the socket options and sets up
     * all the reader / writers
     *
     * @param socket The socket to bind to
     */
    public void connect(@NotNull Socket socket) throws Exception {
        if(this._socket != null) {
            throw new Exception("Already connected");
        }

        this._socket = socket;
        this._socket.setKeepAlive(true);
        this._inputStream = this._socket.getInputStream();
        this._writer = new OutputStreamWriter(this._socket.getOutputStream());
        this._reader = new BufferedReader(new InputStreamReader(this._inputStream));
    }

    /**
     * Gets all the messages the server has sent to this client / this client has received
     *
     * @return The received messages
     */
    public Collection<Message> getRecivedMessages() {
        return this._receivedMessages.values();
    }

    /**
     * Gets all the messages the client has sent to the server / the server has sent to the client
     *
     * @return The sent messages
     */
    public Collection<Message> getSentMessages() {
        return this._sentMessages.values();
    }

    /**
     * Gets a single received message based upon its id
     *
     * @param id The id of the message
     * @return The received message or null if it's not found
     */
    public Message getReceivedMessage(@NotNull String id) {
        return this._receivedMessages.get(id);
    }

    /**
     * Gets a single sent message based upon its id
     *
     * @param id The id of the message
     * @return The sent message or null if it's not found
     */
    public Message getSentMessage(@NotNull String id) {
        return this._sentMessages.get(id);
    }

    /**
     * Gets the socket that this client is bound too
     *
     * @return The bound socket or null if the BaseClient is not yet connected
     */
    public Socket getSocket() {
        return this._socket;
    }

    /**
     * Gets the authentication state of the client
     *
     * @return The auth state of the client
     */
    public AuthState getAuthState() {
        return this._authState;
    }

    /**
     * Sets the auth state of the client and
     * triggers all the auth state changed
     * listeners
     *
     * @param state The new auth state
     */
    public void setAuthState(@NotNull AuthState state) {
        for(AuthStateChanged listener : this._authStateListeners) {
            listener.onAuthStateChanged(this.getAuthState(), state, this);
        }

        Debugger.log(this._getDebuggerCategory(), "Auth state changed from " + this.getAuthState() + " to " + state + " for " + this._socket.getInetAddress().toString());
        this._authState = state;
    }

    /**
     * Adds a disconnect listener to be fired when
     * the connect between this client and the server
     * is dropped
     *
     * @param listener The listener to add
     */
    public void onDisconnect(@NotNull DisconnectListener listener) {
        this._disconnectListeners.add(listener);
    }

    /**
     * Adds an auth state changed listener to be
     * fired when the setAuthState method is called
     * or when the server tells the client about its
     * new auth state
     *
     * @param listener The listener to add
     */
    public void onAuthStateChange(@NotNull AuthStateChanged listener) {
        this._authStateListeners.add(listener);
    }

    /**
     * Adds a MessageReceived listener to be
     * fired when the client receives a message from
     * the server / the server receives a message from the
     * Client that this ServerClient represents
     *
     * @param listener The listener to add
     */
    public void onMessageReceived(@NotNull MessageReceived listener) {
        this._messageReceivedListeners.add(listener);
    }

    /**
     * Gets whether or not the client has been stopped
     * Meaning that all its listeners, writers and readers
     * will have been stopped / closed
     *
     * @return Whether or not the client is disconnected
     */
    public boolean isStopped() {
        return this._closed;
    }

    /**
     * Checks whether or not there is a message waiting
     * from the server / client for non blocking io
     * input
     *
     * @return Whether or not there is a message waiting
     */
    public boolean isMessageWaiting() throws Exception {
        return this._inputStream.available() > 0;
    }

    /**
     * Sends a message to the client / server
     * and if keepMessages is true, generates an
     * id for the message and adds it to the list
     * of sent messages
     *
     * @param msg The message to send
     */
    public void sendMessage(@NotNull Message msg) throws Exception {
        if(!this.isReady()) {
            throw new IOException("Client not ready to send messages");
        }
        else if(this.keepMessages) {
            msg.generateId(this._sentMessages); //generate an id for reply detection
            this._sentMessages.put(msg.getId(), msg);
        }

        if(BaseClient.logHeartbeat || !ServerOptions.HEART_BEAT.equals(msg.request)) {
            Debugger.log(this._getDebuggerCategory(), "Sending message " + msg.toString());
        }
        this._writer.write(msg.toString() + "\n");
        this._writer.flush();
    }

    /**
     * Gets the latest unread message from the
     * server / client. Method will be blocking
     * if isMessageWaiting is not checked before
     * its used as it uses readLine on an IOStream
     *
     * @throws IOException Null message or client not ready to received messages
     * @throws InvalidMessageException JSON parsing of the message failed
     * @return The message
     */
    public Message getMessage() throws Exception {
        if(!this.isReady()) {
            throw new IOException("Not ready to get messages");
        }

        String line = this._reader.readLine();

        if(line == null) {
            throw new Exception("Null message received");
        }

        try {
            Message message = Message.fromJson(line, this);

            if(BaseClient.logHeartbeat || !ServerOptions.HEART_BEAT.equals(message.request)) {
                Debugger.log(this._getDebuggerCategory(), "Got message " + line);
            }

            return message;
        }
        catch(JsonSyntaxException e) {
            Debugger.log(this._getDebuggerCategory(), "Got message " + line);
            throw new InvalidMessageException("JSON parsing failed: " + e.getMessage());
        }
    }

    /**
     * Handles the receiving of a message by
     * adding it to the received messages if
     * keepMessages is enabled and fires any
     * linked reply events on the message the
     * message passed is a response too
     *
     * @param message The message to handle
     */
    public void handleMessage(@NotNull Message message) throws Exception {
        if (this.keepMessages) {
            this._receivedMessages.put(message.getId(), message);
            message.loadResponseTo(this._sentMessages);

            if(message.getResponseTo() != null) {
                message.getResponseTo().triggerReplyEvents(message);
            }
        }

        for (MessageReceived listener : this._messageReceivedListeners) {
            listener.onMessageReceived(BaseClient.this, message);
        }
    }

    /**
     * Whether or not the client and socket are ready
     * to received and send messages. If the socket or client
     * have been closed then this will always be false
     *
     * @return Whether the client is ready for IO actions
     */
    public boolean isReady() {
        return !this._closed && this._socket != null && !this._socket.isClosed() && this._socket.isConnected();
    }

    /**
     * "Closes down" the client, shuts down all the readers, writers
     * and the bound socket then calls all the disconnect listeners
     */
    public void disconnect() {
        this._closed = true;

        String boundAddress = this._socket.getInetAddress().getHostAddress();

        try {
            if (this._reader != null) {
                this._reader.close();
            }

            if (this._writer != null) {
                this._writer.close();
            }

            if (this._socket != null) {
                this._socket.close();
            }
        }
        catch (Exception e) {
            Debugger.log(this._getDebuggerCategory(), "Failed to stop reader / writer / socket: " + e.toString());
        }

        this._socket = null;

        for (DisconnectListener listener : this._disconnectListeners) {
            listener.onDisconnect(this);
        }

        Debugger.log(this._getDebuggerCategory(), "Client " + boundAddress + " disconnected");
    }

    protected String _getDebuggerCategory() {
        return this.getClass().getSimpleName();
    }
}