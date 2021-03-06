package uk.co.maxtingle.communication.client;

import uk.co.maxtingle.communication.Debugger;
import uk.co.maxtingle.communication.client.events.AuthStateChanged;
import uk.co.maxtingle.communication.client.events.DisconnectListener;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.common.events.MessageReceived;
import uk.co.maxtingle.communication.server.ServerOptions;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
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
    private ArrayList<Message>            _receivedMessages         = new ArrayList<Message>();
    private ArrayList<Message>            _sentMessages             = new ArrayList<Message>();
    private AuthState                     _authState                = AuthState.CONNECTED;

    //auth details
    private String _magic;
    private String _username;
    private String _password;

    //settings
    public boolean isRealClient = true; //if this is a server client or an actual client talking to a server
    public boolean keepMessages = false;

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

        this._socket = socket;
        this._writer = new OutputStreamWriter(this._socket.getOutputStream());
        this._inputStream = this._socket.getInputStream();
        this._reader = new BufferedReader(new InputStreamReader(this._inputStream));
    }

    public ArrayList<Message> getRecivedMessages() {
        return this._receivedMessages;
    }

    public ArrayList<Message> getSentMessages() {
        return this._sentMessages;
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

    public void sendMessage(Message msg) throws IOException {
        if(!this.isReady()) {
            throw new IOException("Client not ready to send messages");
        }
        else if(this.keepMessages) {
            this._sentMessages.add(msg);
        }

        Debugger.log(this._getDebuggerCategory(), "Sending message " + msg.request + " encoded to " + msg.toString());

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

    public void listenForReplies() {
        if(this._listeningForReplies || !this.isReady()) {
            return;
        }

        this._listeningForReplies = true;
        final Client self = this; //stupid java lack of delegates means that inner class changes this and need to preserve this
        this._replyListener = new Thread(new Runnable()
        {
            @Override
            public void run() {
                Debugger.log("Client", "Listening for replies");

                try {
                    while (isReady()) {

                        if (!isMessageWaiting()) {
                            continue;
                        }

                        String json = _reader.readLine();
                        Debugger.log(_getDebuggerCategory(), "Received message " + json);
                        Message reply = Message.fromJson(json, self);
                        Debugger.log(_getDebuggerCategory(), "Received message text " + reply.request);

                        if (keepMessages) {
                            _receivedMessages.add(reply);
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
                            listener.onMessageReceived(self, reply);
                        }
                    }
                }
                catch(Exception e) {
                    disconnect();
                    Debugger.log(_getDebuggerCategory(), "Failed to read reply from server socket " + e.toString());
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
    }

    private String _getDebuggerCategory() {
        return this.isRealClient ? "Client" : "Server-Client";
    }
}