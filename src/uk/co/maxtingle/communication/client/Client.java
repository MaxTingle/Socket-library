package uk.co.maxtingle.communication.client;

import com.google.gson.JsonSyntaxException;
import uk.co.maxtingle.communication.client.events.AuthStateChanged;
import uk.co.maxtingle.communication.client.events.DisconnectListener;
import uk.co.maxtingle.communication.common.AuthException;
import uk.co.maxtingle.communication.common.Debugger;
import uk.co.maxtingle.communication.common.InvalidMessageException;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.common.events.MessageReceived;
import uk.co.maxtingle.communication.server.ServerOptions;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Client
{
    private Socket             _socket;
    private OutputStreamWriter _writer;
    private InputStream        _inputStream;
    private BufferedReader     _reader;

    private boolean _closed = false;
    private boolean _listeningForReplies;
    private Thread  _replyListener;
    private Thread  _heartThread;

    //tracking lists
    private ArrayList<DisconnectListener> _disconnectListeners      = new ArrayList<DisconnectListener>();
    private ArrayList<AuthStateChanged>   _authStateListeners       = new ArrayList<AuthStateChanged>();
    private ArrayList<MessageReceived>    _messageReceivedListeners = new ArrayList<MessageReceived>();
    private HashMap<String, Message>      _receivedMessages         = new HashMap<String, Message>();
    private HashMap<String, Message>      _sentMessages             = new HashMap<String, Message>();
    private AuthState                     _authState                = AuthState.CONNECTED;

    //auth details
    private String _magic;
    private String _username;
    private String _password;

    //settings
    public static boolean logHeartbeat = false;
    public boolean isRealClient = true; //if this is a server client or an actual client talking to a server
    public boolean keepMessages = true; //can disable this for memory usage but doing so will mean manual replies needed

    public Client() {

    }

    public Client(Socket socket) throws Exception {
        this(socket, null, null, null);
    }

    public Client(Socket socket, String sendMagic) throws Exception {
        this(socket, sendMagic, null, null);
    }

    public Client(Socket socket, String username, String password) throws Exception {
        this(socket, null, username, password);
    }

    public Client(Socket socket, String sendMagic, String username, String password) throws Exception {
        this._username = username;
        this._password = password;
        this._magic = sendMagic;

        this.connect(socket);
    }

    public void connect(Socket socket) throws Exception {
        if(this._socket != null) {
            throw new Exception("Already connected");
        }

        this._socket = socket;
        this._socket.setKeepAlive(true);
        this._inputStream = this._socket.getInputStream();
        this._writer = new OutputStreamWriter(this._socket.getOutputStream());
        this._reader = new BufferedReader(new InputStreamReader(this._inputStream));
        this._listenForReplies();
        this._startHeart();
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

        if(Client.logHeartbeat || !ServerOptions.HEART_BEAT.equals(msg.request)) {
            Debugger.log(this._getDebuggerCategory(), "Sending message " + msg.toString());
        }
        this._writer.write(msg.toString() + "\n");
        this._writer.flush();
    }

    public Message getMessage() throws Exception {
        if(!this.isReady()) {
            throw new IOException("Client not ready to get messages");
        }

        String line = this._reader.readLine();

        if(line == null) {
            throw new Exception("Null message received");
        }

        try {
            Message message = Message.fromJson(line, this);

            if(Client.logHeartbeat || !ServerOptions.HEART_BEAT.equals(message.request)) {
                Debugger.log(this._getDebuggerCategory(), "Got message " + line);
            }

            return message;
        }
        catch(JsonSyntaxException e) {
            Debugger.log(this._getDebuggerCategory(), "Got message " + line);
            throw new InvalidMessageException("JSON parsing failed: " + e.getMessage());
        }
    }

    public boolean isListeningForReplies() {
        return this._listeningForReplies;
    }

    public boolean isReady() {
        return !this._closed && this._socket != null && !this._socket.isClosed() && this._socket.isConnected();
    }

    public void disconnect() {
        this._closed = true;

        if(this._replyListener != null && this._replyListener.isAlive()) {
            this._replyListener.interrupt();
            this._replyListener = null;
        }

        if(this._heartThread != null && this._heartThread.isAlive()) {
            this._heartThread.interrupt();
            this._heartThread = null;
        }

        String boundAddress = this._socket.getInetAddress().getHostAddress();

        try {
            if(this._reader != null) {
                this._reader.close();
            }

            if(this._writer != null) {
                this._writer.close();
            }

            if(this._socket != null) {
                this._socket.close();
            }
        }
        catch(Exception e) {
            Debugger.log(this._getDebuggerCategory(), "Failed to stop reader / writer / socket: " + e.toString());
        }

        this._socket = null;

        for(DisconnectListener listener : this._disconnectListeners) {
            listener.onDisconnect(this);
        }

        Debugger.log(this._getDebuggerCategory(), "Client " + boundAddress + " disconnected");
    }

    protected void _startHeart() {
        if(this._heartThread != null || !this.isReady()) {
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
                    if(!Client.this.isStopped()) {
                        Debugger.log(Client.this._getDebuggerCategory(), "Failed to skip first beat " + e.getMessage());
                    }
                }

                while(Client.this.isReady()) {
                    try {
                        Client.this.sendMessage(new Message(ServerOptions.HEART_BEAT));
                        Thread.sleep(ServerOptions.HEART_BMP);
                    }
                    catch(Exception e) {
                        if(!Client.this.isStopped()) {
                            Debugger.log(Client.this._getDebuggerCategory(), "Heart beat failed, client died: " + e.getMessage());
                            Client.this.disconnect();
                        }
                    }
                }
            }
        });
        this._heartThread.start();
    }

    protected void _listenForReplies() {
        if(this._listeningForReplies || !this.isReady()) {
            return;
        }

        this._listeningForReplies = true;
        this._replyListener = new Thread(new Runnable()
        {
            @Override
            public void run() {
                Debugger.log(Client.this._getDebuggerCategory(), "Listening for replies");

                try {
                    while (isReady()) {
                        if (!Client.this.isMessageWaiting()) {
                            continue;
                        }

                        String json = _reader.readLine();

                        if(json == null) {
                            Debugger.log(Client.this._getDebuggerCategory(), "Silent disconnect detected, disconnecting properly");
                            Client.this.disconnect();
                            break;
                        }

                        Debugger.log(_getDebuggerCategory(), "Received message " + json);
                        Message message = Message.fromJson(json, Client.this);

                        if(ServerOptions.HEART_BEAT.equals(message.request)) {
                            continue; //just a heart beat message, take no note
                        }
                        else if (keepMessages) {
                            _receivedMessages.put(message.getId(), message);
                            message.loadResponseTo(_sentMessages);

                            if(message.getResponseTo() != null) {
                                message.getResponseTo().triggerReplyEvents(message);
                            }
                        }

                        if (isRealClient) { //to stop this modifying any server auth
                            //handling special server commands
                            if (ServerOptions.REQUEST_MAGIC.equals(message.request)) { //sending magic
                                if (_magic == null || _magic.trim().equals("")) {
                                    throw new Exception("Server requested magic but no magic to reply with");
                                }

                                setAuthState(AuthState.AWAITING_MAGIC);
                                message.respond(new Message(ServerOptions.SEND_MAGIC, new Object[]{_magic}));
                                continue;
                            }
                            else if (ServerOptions.REQUEST_CREDENTIALS.equals(message.request)) { //sending credentials
                                if (_username == null || _password == null || _username.trim().equals("")) {
                                    throw new Exception("Server requested credentials but no credentials to reply with");
                                }

                                setAuthState(AuthState.AWAITING_CREDENTIALS);

                                Map<String, String> authParams = new HashMap<String, String>();
                                authParams.put("username", _username);
                                authParams.put("password", _password);
                                message.respond(new Message(ServerOptions.SEND_CREDENTIALS, new Object[]{authParams}));
                                continue;
                            }
                            else if (ServerOptions.ACCEPTED_AUTH.equals(message.request)) {
                                setAuthState(AuthState.ACCEPTED);
                                continue;
                            }
                            else if (message.success != null && !message.success && getAuthState() != AuthState.ACCEPTED) { //not authed and reply from server
                                throw new AuthException("Authentication failed: " + message.request);
                            }
                        }

                        for (MessageReceived listener : _messageReceivedListeners) {
                            listener.onMessageReceived(Client.this, message);
                        }
                    }
                }
                catch(AuthException e) {
                    disconnect();
                    Debugger.log(Client.this._getDebuggerCategory(), e.toString());
                }
                catch(Exception e) {
                    disconnect();
                    Debugger.log(Client.this._getDebuggerCategory(), "Failed to read reply from server socket " + e.toString());
                }

                _listeningForReplies = false;
            }
        });
        this._replyListener.start();
    }

    protected String _getDebuggerCategory() {
        return this.isRealClient ? "Client" : "Server-Client";
    }
}