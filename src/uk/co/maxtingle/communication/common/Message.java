package uk.co.maxtingle.communication.common;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import uk.co.maxtingle.communication.common.events.MessageReceived;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Message
{
    private static final Gson         _jsonParser = new Gson();
    private static final SecureRandom _random     = new SecureRandom();

    public  Object[]   params;
    public  String     request;
    public  Boolean    success;
    private String     _id;
    private BaseClient _client; //needs to be private so not serialized
    String _responseToId; //the message this message is a response to
    private Message _responseToMessage;
    private ArrayList<MessageReceived> _replyListeners = new ArrayList<MessageReceived>();

    /* Client making requests */
    public Message(String request) {
        this.request = request;
    }

    public Message(String request, Object[] params) {
        this.params = params;
        this.request = request;
    }

    /* Server responding */
    public Message(boolean success, String error) {
        this.request = error;
        this.success = success;
    }

    public Message(boolean success, Object[] returnData) {
        this(success, "", returnData);
    }

    public Message(boolean success, String responseText, Object[] returnData) {
        this.params = returnData;
        this.request = responseText;
        this.success = success;
    }

    public String getId() {
        return this._id;
    }

    public void generateId(Map<?, ?> usedKeys) throws Exception {
        if(this._id != null) {
            throw new Exception("Id already generated");
        }

        String generated = new BigInteger(130, Message._random).toString(32);
        if(usedKeys.containsKey(generated)) {
            this.generateId(usedKeys);
        }
        else {
            this._id = generated;
        }
    }

    public void onReply(MessageReceived listener) {
        this._replyListeners.add(listener);
    }

    public void triggerReplyEvents(Message received) throws Exception {
        for(MessageReceived event : this._replyListeners) {
            event.onMessageReceived(this._client, received);
        }
    }

    public Message getResponseTo() {
        return this._responseToMessage;
    }

    public void loadResponseTo(HashMap<String, Message> sentMessages) throws Exception {
        if(this._responseToMessage != null) {
            throw new Exception("Response to message already loaded");
        }

        this._responseToMessage = sentMessages.get(this._responseToId);
    }

    public void respond(Message message) throws Exception {
        if(this._client == null) {
            throw new Exception("Message not from client, cannot respond.");
        }

        message._responseToId = this._id;
        this._client.sendMessage(message);
    }

    @Override
    public Message clone() {
        Message clone = new Message(this.request, this.params);
        clone._responseToMessage = this._responseToMessage;
        clone._replyListeners = this._replyListeners;
        clone._responseToId = this._responseToId;
        clone._client = this._client;
        return clone;
    }

    public String toString() {
        return Message._jsonParser.toJson(new SerializableMessage(this));
    }

    public static Message fromJson(String json, BaseClient baseClient) throws JsonSyntaxException {
        SerializableMessage serializableMessage = Message._jsonParser.fromJson(json, SerializableMessage.class);
        Message message = new Message(serializableMessage.request);
        message.success = serializableMessage.success;
        message.params = serializableMessage.params;
        message._client = baseClient;
        message._responseToId = serializableMessage.responseTo;
        message._id = serializableMessage.id;

        return message;
    }

    void setClient(BaseClient baseClient) {
        this._client = baseClient;
    }
}
