package com.maxtingle.communication;

import uk.co.maxtingle.communication.Debugger;
import uk.co.maxtingle.communication.common.Message;

import java.io.*;
import java.net.Socket;

public class Client
{
    private Socket             _socket;
    private OutputStreamWriter _writer;
    private InputStream        _inputStream;
    private BufferedReader     _reader;

    private boolean _closing = false;
    private boolean _listeningForReplies;
    private Thread  _replyListener;

    public Client(Socket socket) throws Exception {
        this._socket = socket;
        this._writer = new OutputStreamWriter(this._socket.getOutputStream());
        this._inputStream = this._socket.getInputStream();
        this._reader = new BufferedReader(new InputStreamReader(this._inputStream));
    }

    public void sendMessage(Message msg) throws IOException {
        if(!this.isReady()) {
            throw new IOException("Client not ready to send messages");
        }

        Debugger.log("Client", "Sending message " + msg.message + " encoded to " + msg.convertToJson());

        this._writer.write(msg.convertToJson() + "\n");
        this._writer.flush();
    }

    public boolean isMessageWaiting() throws Exception {
        return this._inputStream.available() > 0;
    }

    public Message getMessage() throws Exception {
        String line = this._reader.readLine();
        Debugger.log("Server", "Got message " + line);
        return Message._fromJson(line);
    }

    public void listenForReplies() {
        if(this._listeningForReplies || !this.isReady()) {
            return;
        }

        this._listeningForReplies = true;
        this._replyListener = new Thread(new Runnable()
        {
            @Override
            public void run() {
                Debugger.log("Client", "Listening for replies");

                while(isReady()) {
                    try {
                        if(!isMessageWaiting()) {
                            continue;
                        }

                        String json = _reader.readLine();
                        Debugger.log("Client", "Received reply " + json);
                        Message reply = Message._fromJson(json);
                        Debugger.log("Client", "Received reply text " + reply.message);
                    }
                    catch(Exception e) {
                        Debugger.log("Client", "Failed to read reply from server socket " + e.toString());
                    }
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
        return !this._closing && this._socket != null && !this._socket.isClosed() && this._socket.isConnected();
    }

    public void disconnect() throws IOException {
        this._closing = true;

        if(this._replyListener != null && this._replyListener.isAlive()) {
            this._replyListener.interrupt();
            this._replyListener = null;
        }

        this._reader.close();
        this._writer.close();
        this._socket.close();
        this._socket = null;
        this._closing = false;
    }
}