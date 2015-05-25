package uk.co.maxtingle.communication.common;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import uk.co.maxtingle.communication.common.events.MessageReceived;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * A message that has been sent between the
 * client & server
 */
public class Message
{
    private static final Gson         _jsonParser = new Gson();
    private static final SecureRandom _random     = new SecureRandom();

    /**
     * The parameters of the request,
     * can be instances of any object.
     * Use transient to stop serialization
     * of an object variable
     */
    public  Object[]   params;

    /**
     * The request you are wanting to perform,
     * do not prefix it with __ as they are used
     * by the server and client internally for
     * things such as authentication
     */
    public  String     request;

    /**
     * If this message is a reply to another message
     * then this will be whether or not the message
     * its a reply to was a success or not
     *
     * Otherwise it is always null
     */
    public  Boolean    success;


    String             _responseToId; //the message this message is a response to
    private String     _id;
    private BaseClient _client; //needs to be private so not serialized
    private Message    _responseToMessage;
    private ArrayList<MessageReceived> _replyListeners = new ArrayList<MessageReceived>();

    /**
     * Creates a new instance of the message and
     * sets the request variable to the one provided
     *
     * @param request The request to make
     */
    public Message(@Nullable String request) {
        this.request = request;
    }

    /**
     * Creates a new instance of the message and
     * sets the variables given to the public versions
     * of them on the object
     *
     * @param request The request to make
     * @param params  The parameters of the request
     */
    public Message(@Nullable String request, @Nullable Object[] params) {
        this.params = params;
        this.request = request;
    }

    /**
     * If this message is a server message / the server
     * responding to a client message then this creates
     * a new instance of the Message and sets the success
     * and request variables to those provided
     *
     * @param success Whether the message this is a reply to
     *                was a success or not
     * @param error   The request to send back
     *
     */
    public Message(@NotNull boolean success, @Nullable String error) {
        this.request = error;
        this.success = success;
    }

    /**
     * If this message is a server message / the server
     * responding to a client message then this creates
     * a new instance of the Message and sets the success
     * and request variables to those provided
     *
     * @param success    Whether the message this is a reply to
     *                   was a success or not
     * @param returnData The data to send back
     *
     */
    public Message(@NotNull boolean success, @Nullable Object[] returnData) {
        this(success, null, returnData);
    }

    /**
     * If this message is a server message / the server
     * responding to a client message then this creates
     * a new instance of the Message and sets the success
     * and request variables to those provided
     *
     * @param success      Whether the message this is a reply to
     *                     was a success or not
     * @param responseText The request to send back
     * @param returnData   The data to send back
     *
     */
    public Message(boolean success, String responseText, Object[] returnData) {
        this.params = returnData;
        this.request = responseText;
        this.success = success;
    }

    /**
     * Gets the id of the message if it has been set
     * Note that it will only be set if the place it has
     * come from has keepMessages set to true
     *
     * @return The id of this message or null if it is not set yet
     */
    public String getId() {
        return this._id;
    }

    /**
     * Generates an id for this message and avoids
     * using once already used
     *
     * @param usedKeys The keys not to use because they're already used
     * @throws Exception ID is already generated
     */
    public void generateId(@Nullable Map<?, ?> usedKeys) throws Exception {
        if(this._id != null) {
            throw new Exception("Id already generated");
        }

        String generated = new BigInteger(130, Message._random).toString(32);
        if(usedKeys != null && usedKeys.containsKey(generated)) {
            this.generateId(usedKeys);
        }
        else {
            this._id = generated;
        }
    }

    /**
     * Adds a MessageReceived listener to be fired when a
     * reply is sent to this message
     *
     * @param listener The listener to add
     */
    public void onReply(@NotNull MessageReceived listener) {
        this._replyListeners.add(listener);
    }

    /**
     * Sets the message that has responded to this message
     * and triggers all the reply handlers for this message
     * with the message provided
     *
     * @param received The message that is a reply to this message
     * @throws Exception One of the reply listeners threw an exception
     */
    public void triggerReplyEvents(@NotNull Message received) throws Exception {
        for(MessageReceived event : this._replyListeners) {
            event.onMessageReceived(this._client, received);
        }
    }

    /**
     * Gets the message that this a response to
     *
     * @return The message this a response to or null if it is not a response to any messages
     */
    public Message getResponseTo() {
        return this._responseToMessage;
    }

    /**
     * Loads the message that this a response to from a hashmap
     * of previously sent messages
     *
     * @param sentMessages The messages that have been sent before, with the string as their id
     * @throws Exception Response to message already loaded
     */
    public void loadResponseTo(@NotNull HashMap<String, Message> sentMessages) throws Exception {
        if(this._responseToMessage != null) {
            throw new Exception("Response to message already loaded");
        }

        this._responseToMessage = sentMessages.get(this._responseToId);
    }

    /**
     * Sends a new message to the place that sent this message, in reply
     * to this message
     *
     * @param message The message to respond with
     * @throws Exception This message doesn't have an associated client
     */
    public void respond(@NotNull Message message) throws Exception {
        if(this._client == null) {
            throw new Exception("Message not from client, cannot respond.");
        }

        message._responseToId = this._id;
        this._client.sendMessage(message);
    }

    /**
     * Creates a clone of this message in every way
     * exception the id of this message
     *
     * @return The clone of this message
     */
    @Override
    public Message clone() {
        Message clone = new Message(this.request, this.params);
        clone._responseToMessage = this._responseToMessage;
        clone._replyListeners = this._replyListeners;
        clone._responseToId = this._responseToId;
        clone._client = this._client;
        return clone;
    }

    /**
     * Converts this message into a SerialzableMessage and then
     * into a JSON string for sending to the opposing end
     *
     * @return String The JSON stirng
     */
    public String toString() {
        return Message._jsonParser.toJson(new SerializableMessage(this));
    }

    /**
     * Creates a message from the JSON provided by first
     * deserializing it into a Serializable message and then
     * copying over the data into a new instance of a Message
     *
     * @throws JsonSyntaxException Failed to parse the JSON
     * @return The message
     */
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
