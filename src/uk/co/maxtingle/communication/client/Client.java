package uk.co.maxtingle.communication.client;

import uk.co.maxtingle.communication.Debugger;
import uk.co.maxtingle.communication.client.events.AuthStateChanged;
import uk.co.maxtingle.communication.client.events.DisconnectListener;
import uk.co.maxtingle.communication.common.AuthException;
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
        this._inputStream = this._socket.getInputStream();
        this._writer = new OutputStreamWriter(this._socket.getOutputStream());
        this._reader = new BufferedReader(new InputStreamReader(this._inputStream));
        this._listenForReplies();
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

        Debugger.log(this._getDebuggerCategory(), "Sending message " + msg.toString());
        this._writer.write(msg.toString() + "\n");
        this._writer.flush();
    }

    public Message getMessage() throws Exception {
        if(!this.isReady()) {
            throw new IOException("Client not ready to get messages");
        }

        String line = this._reader.readLine();
        Debugger.log("Server", "Got message " + line);
        return Message.fromJson(line, this);
    }

    private void _listenForReplies() {
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
                        Message reply = Message.fromJson(json, Client.this);

                        if (keepMessages) {
                            _receivedMessages.put(reply.getId(), reply);
                            reply.loadResponseTo(_sentMessages);

                            if(reply.getResponseTo() != null) {
                                reply.getResponseTo().triggerReplyEvents(reply);
                            }
                        }

                        if (isRealClient) { //to stop this modifying any server auth
                            //handling special server commands
                            if (reply.request.equals(ServerOptions.requestMagicString)) { //sending magic
                                if (_magic == null || _magic.trim().equals("")) {
                                    throw new Exception("Server requested magic but no magic to reply with");
                                }

                                setAuthState(AuthState.AWAITING_MAGIC);
                                reply.respond(new Message(ServerOptions.sendMagicString, new Object[]{_magic}));
                                continue;
                            }
                            else if (reply.request.equals(ServerOptions.requestCredentialsString)) { //sending credentials
                                if (_username == null || _password == null || _username.trim().equals("")) {
                                    throw new Exception("Server requested credentials but no credentials to reply with");
                                }

                                setAuthState(AuthState.AWAITING_CREDENTIALS);

                                Map<String, String> authParams = new HashMap<String, String>();
                                authParams.put("username", _username);
                                authParams.put("password", _password);
                                reply.respond(new Message(ServerOptions.sendCredentialsString, new Object[]{authParams}));
                                continue;
                            }
                            else if (reply.request.equals(ServerOptions.acceptedAuthString)) {
                                setAuthState(AuthState.ACCEPTED);
                                continue;
                            }
                            else if (reply.success != null && !reply.success && getAuthState() != AuthState.ACCEPTED) { //not authed and reply from server
                                throw new AuthException("Authentication failed: " + reply.request);
                            }
                        }

                        for (MessageReceived listener : _messageReceivedListeners) {
                            listener.onMessageReceived(Client.this, reply);
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

        try {
            this._reader.close();
            this._writer.close();
            this._socket.close();
        }
        catch(Exception e) {
            Debugger.log(this._getDebuggerCategory(), "Failed to stop reader / writer / socket " + e.toString());
        }

        this._socket = null;

        for(DisconnectListener listener : this._disconnectListeners) {
            listener.onDisconnect(this);
        }

        Debugger.log(this._getDebuggerCategory(), "Client disconnected");
    }

    private String _getDebuggerCategory() {
        return this.isRealClient ? "Client" : "Server-Client";
    }
}