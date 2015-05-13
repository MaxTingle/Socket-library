package uk.co.maxtingle.communication.common;

import com.google.gson.JsonSyntaxException;
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

public abstract class BaseClient
{
    protected Socket             _socket;
    protected OutputStreamWriter _writer;
    protected InputStream        _inputStream;
    protected BufferedReader     _reader;

    protected boolean _closed = false;

    //tracking lists
    protected ArrayList<DisconnectListener> _disconnectListeners      = new ArrayList<DisconnectListener>();
    protected ArrayList<AuthStateChanged>   _authStateListeners       = new ArrayList<AuthStateChanged>();
    protected ArrayList<MessageReceived>    _messageReceivedListeners = new ArrayList<MessageReceived>();
    protected HashMap<String, Message>      _receivedMessages         = new HashMap<String, Message>();
    protected HashMap<String, Message>      _sentMessages             = new HashMap<String, Message>();
    protected AuthState                     _authState                = AuthState.CONNECTED;

    //settings
    public static boolean logHeartbeat = false;
    public        boolean keepMessages = true; //can disable this for memory usage but doing so will mean manual replies needed

    public void connect(Socket socket) throws Exception {
        if(this._socket != null) {
            throw new Exception("Already connected");
        }

        this._socket = socket;
        this._socket.setKeepAlive(true);
        this._inputStream = this._socket.getInputStream();
        this._writer = new OutputStreamWriter(this._socket.getOutputStream());
        this._reader = new BufferedReader(new InputStreamReader(this._inputStream));
    }

    public Collection<Message> getRecivedMessages() {
        return this._receivedMessages.values();
    }

    public Collection<Message> getSentMessages() {
        return this._sentMessages.values();
    }

    public Message getReceivedMessage(String id) {
        return this._receivedMessages.get(id);
    }

    public Message getSentMessage(String id) {
        return this._sentMessages.get(id);
    }

    public Socket getSocket() {
        return this._socket;
    }

    public AuthState getAuthState() {
        return this._authState;
    }

    public void setAuthState(AuthState state) {
        for(AuthStateChanged listener : this._authStateListeners) {
            listener.onAuthStateChanged(this.getAuthState(), state, this);
        }

        Debugger.log(this._getDebuggerCategory(), "Auth state changed from " + this.getAuthState() + " to " + state + " for " + this._socket.getInetAddress().toString());
        this._authState = state;
    }

    public void onDisconnect(DisconnectListener listener) {
        this._disconnectListeners.add(listener);
    }

    public void onAuthStateChange(AuthStateChanged listener) {
        this._authStateListeners.add(listener);
    }

    public void onMessageReceived(MessageReceived listener) {
        this._messageReceivedListeners.add(listener);
    }

    public boolean isStopped() {
        return this._closed;
    }

    public boolean isMessageWaiting() throws Exception {
        return this._inputStream.available() > 0;
    }

    public void sendMessage(Message msg) throws Exception {
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

    public void handleMessage(Message message) throws Exception {
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

    public boolean isReady() {
        return !this._closed && this._socket != null && !this._socket.isClosed() && this._socket.isConnected();
    }

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