package uk.co.maxtingle.communication.client;

import uk.co.maxtingle.communication.common.AuthState;
import uk.co.maxtingle.communication.common.BaseClient;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.common.exception.AuthException;
import uk.co.maxtingle.communication.debug.Debugger;
import uk.co.maxtingle.communication.server.ServerOptions;

import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Client extends BaseClient
{
    private Thread  _heartThread;
    private Thread  _replyListener;
    private boolean _listeningForReplies;

    //auth details
    private String _magic;
    private String _username;
    private String _password;

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

    public boolean isListeningForReplies() {
        return this._listeningForReplies;
    }

    @Override
    public void connect(Socket socket) throws Exception {
        super.connect(socket);
        this._listenForReplies(); //the server handles its own replies all on one thread not one thread per client
        this._startHeart();
    }

    @Override
    public void disconnect() {
        if (this._replyListener != null && this._replyListener.isAlive()) {
            this._replyListener.interrupt();
            this._replyListener = null;
        }

        if (this._heartThread != null && this._heartThread.isAlive()) {
            this._heartThread.interrupt();
            this._heartThread = null;
        }

        super.disconnect();
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

                        Message message = Client.this.getMessage();
                        if(ServerOptions.HEART_BEAT.equals(message.request)) {
                            continue; //just a heartbeat message, ignore it
                        }

                        /* Handle the message */
                        if(Client.this.getAuthState() != AuthState.ACCEPTED) {
                            Client.this._handleAuth(message);
                        }
                        else {
                            Client.this.handleMessage(message);
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

                Client.this._listeningForReplies = false;
            }
        });
        this._replyListener.start();
    }

    protected void _handleAuth(Message message) throws Exception {
        //handling special server commands
        if (ServerOptions.REQUEST_MAGIC.equals(message.request)) { //sending magic
            if (Client.this._magic == null || Client.this._magic.trim().equals("")) {
                throw new Exception("Server requested magic but no magic to reply with");
            }

            Client.this.setAuthState(AuthState.AWAITING_MAGIC);
            message.respond(new Message(ServerOptions.SEND_MAGIC, new Object[]{_magic}));
        }
        else if (ServerOptions.REQUEST_CREDENTIALS.equals(message.request)) { //sending credentials
            if (Client.this._username == null || Client.this._password == null || Client.this._username.trim().equals("")) {
                throw new Exception("Server requested credentials but no credentials to reply with");
            }

            Client.this.setAuthState(AuthState.AWAITING_CREDENTIALS);

            Map<String, String> authParams = new HashMap<String, String>();
            authParams.put("username", Client.this._username);
            authParams.put("password", Client.this._password);
            message.respond(new Message(ServerOptions.SEND_CREDENTIALS, new Object[]{authParams}));
        }
        else if (ServerOptions.ACCEPTED_AUTH.equals(message.request)) {
            setAuthState(AuthState.ACCEPTED);
        }
        else if (message.success != null && !message.success && getAuthState() != AuthState.ACCEPTED) { //not authed and reply from server
            throw new AuthException("Authentication failed: " + message.request);
        }
    }
}
