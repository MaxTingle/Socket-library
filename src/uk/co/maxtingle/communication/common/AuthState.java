package uk.co.maxtingle.communication.common;

/**
 * The authentication states of a client
 */
public enum AuthState
{
    /**
     * The client is connected to the server
     * but has not been authenticated yet
     */
    CONNECTED,

    /**
     * The server has requested the magic string
     * from the client to identify if it is the
     * correct type of client
     */
    AWAITING_MAGIC,

    /**
     * The server has requested authentication
     * details from the client and is awaiting
     * a reply
     */
    AWAITING_CREDENTIALS,

    /**
     * The client is authenticated with the server
     * and ready to send messages
     */
    ACCEPTED
}
