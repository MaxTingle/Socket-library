package uk.co.maxtingle.communication.common;

import com.google.gson.Gson;
import uk.co.maxtingle.communication.client.Client;

public class Message
{
    private static final Gson _jsonParser = new Gson();

    public  Object[] params;
    public  String   request;
    public  Boolean  success;
    private Client   _client; //needs to be private so not serialized
    private Message  _responseTo; //the message this message is a response to

    public Message(String request) {
        this.request = request;
    }

    public Message(boolean success, String error) {
        this.request = error;
        this.success = success;
    }

    public Message(String request, Object[] params) {
        this.params = params;
        this.request = request;
    }

    public Message getResponseTo() {
        return this._responseTo;
    }

    public void respond(Message message) throws Exception {
        if(this._client == null) {
            throw new Exception("Message not from client, cannot respond.");
        }

        message._responseTo = this;
        this._client.sendMessage(message);
    }

    public String toString() {
        return Message._jsonParser.toJson(new SerializableMessage(this));
    }

    public static Message fromJson(String json, Client client) {
        return Message._fromJson(json, client, false);
    }

    private static Message _fromJson(String json, Client client, boolean isResponseToMessage) {
        SerializableMessage serializableMessage = Message._jsonParser.fromJson(json, SerializableMessage.class);
        Message message = new Message(serializableMessage.request);
        message.success = serializableMessage.success;
        message.params = serializableMessage.params;
        message._client = client;

        if(!isResponseToMessage && serializableMessage.responseTo != null) { //only want to load one layer of response to
            message._responseTo = Message._fromJson(serializableMessage.responseTo, client, true);
        }

        return message;
    }

    void setClient(Client client) {
        this._client = client;
    }
}
