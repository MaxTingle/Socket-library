package uk.co.maxtingle.communication.common;

public class SerializableMessage
{
    public Object[]           params;
    public String             request;
    public Boolean            success;
    public String id;
    public String responseTo; //the message this message is a response to

    public SerializableMessage(Message message) {
        this.params = message.params;
        this.request = message.request;
        this.success = message.success;
        this.id = message.getId();
        this.responseTo = message._responseToId;
    }
}