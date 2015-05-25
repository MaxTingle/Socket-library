package uk.co.maxtingle.communication.common;

/**
 * The message that is sent between the server and client
 * then turned into a proper Message instance based upon
 * the data in this class. There are no docblocks in this
 * class because all the variables are just copies of the
 * Message variables with a generic type
 */
public class SerializableMessage
{
    public Object[]           params;
    public String             request;
    public Boolean            success;
    public String id;
    public String responseTo; //the message this message is a response to

    /**
     * Creates a new instance of the SerializableMessage and
     * loads in the params, request, success, id and response to id
     * of the message
     */
    public SerializableMessage(Message message) {
        this.params = message.params;
        this.request = message.request;
        this.success = message.success;
        this.id = message.getId();
        this.responseTo = message._responseToId;
    }
}