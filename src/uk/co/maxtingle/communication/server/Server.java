package uk.co.maxtingle.communication.server;

import uk.co.maxtingle.communication.Debugger;
import uk.co.maxtingle.communication.client.AuthState;
import uk.co.maxtingle.communication.client.Client;
import uk.co.maxtingle.communication.common.Message;
import uk.co.maxtingle.communication.common.events.MessageReceived;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Map;

public class Server
{
    private ServerOptions _options;
    private ServerSocket  _listener;
    private Thread        _clientListenerThread;
    private Thread        _messageListenerThread;
    private boolean _closing = false;

    private ArrayList<Client>          _clients                  = new ArrayList<Client>();
    private ArrayList<MessageReceived> _messageReceivedListeners = new ArrayList<MessageReceived>();

    public Server() {
        this._options = new ServerOptions()
        {
        };
    }

    public Server(int serverPort) {
        this._options = new ServerOptions();
        this._options.port = serverPort;
    }

    public Server(ServerOptions options) throws Exception {
        if (options.useCredentials && options.authHandler == null) {
            throw new Exception("Cannot use credentials without auth handler.");
        }
        else if (options.useMagic && (options.expectedMagic == null || options.expectedMagic.trim().equals(""))) {
            throw new Exception("Cannot use expectedMagic without setting expected expectedMagic value");
        }

        this._options = options;
    }

    public boolean isReady() {
        return !this._closing && this._listener != null && this._listener.isBound() && !this._listener.isClosed();
    }

    public ArrayList<Client> getClients() {
        return this._clients;
    }

    public void onMessageReceived(MessageReceived listener) {
        this._messageReceivedListeners.add(listener);
    }

    public ArrayList<Client> getAcceptedClients() {
        if(!this._options.useMagic && !this._options.useCredentials) {
            return this._clients;
        }

        ArrayList<Client> matchingClients = new ArrayList<Client>();

        for(Client client : this._clients) {
            if((this._options.useCredentials || (!this._options.useCredentials && this._options.useMagic))
               && client.getAuthState() == AuthState.ACCEPTED)
            {
                matchingClients.add(client);
            }
        }

        return matchingClients;
    }

    public ArrayList<Client> getClientsInState(AuthState state) {
        ArrayList<Client> matches = new ArrayList<Client>();

        for(Client client : this._clients) {
            if(client.getAuthState() == state) {
                matches.add(client);
            }
        }

        return matches;
    }

    public void start() throws Exception {
        this._listener = new ServerSocket(this._options.port);

        Debugger.log("Server", "Listening on " + this._listener.getInetAddress().toString() + ":" + this._options.port);
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

        this._clientListenerThread = new Thread(new Runnable()
        {
            @Override
            public void run() {
                Debugger.log("Server", "Listening for clients");

                while (isReady()) {
                    try {
                        Client client = new Client(_listener.accept());
                        client.isRealClient = false;
                        client.keepMessages = _options.keepMessages;
                        _clients.add(client);
                        Debugger.log("Server", "Accepted new client");

                        if(_options.useMagic) {
                            client.setAuthState(AuthState.AWAITING_MAGIC);
                            client.sendMessage(new Message(ServerOptions.requestMagicString));
                        }
                        else if(_options.useCredentials) {
                            client.setAuthState(AuthState.AWAITING_CREDENTIALS);
                            client.sendMessage(new Message(ServerOptions.requestCredentialsString));
                        }
                        else {
                            client.setAuthState(AuthState.ACCEPTED); //no auth in place
                            client.sendMessage(new Message(true, ServerOptions.acceptedAuthString));
                        }
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
        {
            @Override
            public void run() {
                Debugger.log("Server", "Listening for messages");

                while (isReady()) {
                    try {
                        for(int i = _clients.size() - 1; i != -1; i--) { //will be dynamically removing from list
                            Client client = _clients.get(i);
                            if (!client.isMessageWaiting()) {
                                continue;
                            }

                            Message message = client.getMessage();
                            if (client.getAuthState() != AuthState.ACCEPTED) {
                                if (ServerOptions.sendMagicString.equals(message.request) || ServerOptions.sendCredentialsString.equals(message.request)) {
                                    try {
                                        _handleSpecialMessage(client, message); //sent special message
                                    }
                                    catch(Exception e) { //failed auth
                                        message.respond(new Message(false, e.toString()));
                                    }
                                }
                                else { //asked for expectedMagic / credentials, didn't get it
                                    message.respond(new Message(false, "Not authenticated, request rejected"));
                                    _disconnectClient(client);
                                }
                            }
                            else {
                                try {
                                    for (MessageReceived listener : _messageReceivedListeners) {
                                        listener.onMessageReceived(client, message);
                                    }
                                }
                                catch(Exception e) {
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
        this._messageListenerThread.start();
    }

    private void _handleSpecialMessage(Client client, Message message) throws Exception {
        if(message.params.length != 1) { //not got any params
            throw new Exception("Incorrect number of params");
        }
        else if(message.params[0] == null) {
            throw new Exception("Null param given");
        }
        else if(_options.useMagic && client.getAuthState() == AuthState.AWAITING_MAGIC) {
            if(!(message.params[0] instanceof String)) {
                //not sent a string
                throw new Exception("Incorrect type of param");
            }
            else if(!_options.expectedMagic.equals(message.params[0])) {
                throw new Exception("Incorrect magic");
            }
            else if(_options.useCredentials) { //stage 1 complete, ask for credentials now
                client.setAuthState(AuthState.AWAITING_CREDENTIALS);
                message.respond(new Message(true, ServerOptions.requestCredentialsString));
            }
            else { //expectedMagic only, they have passed auth
                client.setAuthState(AuthState.ACCEPTED);
                message.respond(new Message(true, ServerOptions.acceptedAuthString));
            }
        }
        else if(_options.useCredentials && client.getAuthState() == AuthState.AWAITING_CREDENTIALS) {
            String username;
            String password;

            try {
                if(!(message.params[0] instanceof Map)) {
                    throw new Exception("Invalid username and password type. params[0] must be an associative array (Map) with username and password in.");
                }

                @SuppressWarnings("unchecked")
                Map<String, String> hashMap = (Map<String, String>) message.params[0];

                username = hashMap.get("username");
                password = hashMap.get("password");
            }
            catch(Exception e) {
                throw new Exception("Invalid username and password type. params[0] must be an associative array (Map) with username and password in.");
            }

            if(_options.authHandler.checkAuth(username, password, client, message)) {
                client.setAuthState(AuthState.ACCEPTED);
                message.respond(new Message(true, ServerOptions.acceptedAuthString));
            }
            else {
                throw new Exception("Invalid credentials");
            }
        }
    }

    private void _disconnectClient(Client client) throws Exception {
        client.disconnect();
        this._clients.remove(client);
    }
}