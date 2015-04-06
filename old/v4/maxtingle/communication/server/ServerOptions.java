package uk.co.maxtingle.communication.server;

import uk.co.maxtingle.communication.server.events.AuthReceived;

public class ServerOptions
{
    public static final String requestMagicString       = "__SEND_MAGIC__";
    public static final String requestCredentialsString = "__SEND_CREDENTIALS__";
    public static final String acceptedAuthString       = "__AUTHENTICATED__";
    public static final String sendMagicString          = "__MAGIC__";
    public static final String sendCredentialsString    = "__CREDENTIALS__";

    /** the port to broadcast on */
    public int port = 8080;

    /** whether or not a Client should be told to keep all sent and received messages */
    public boolean keepMessages = true;

    /** whether or not to auth with expectedMagic first */
    public boolean useMagic = false;

    /** the expectedMagic to auth with */
    public String expectedMagic;

    /** whether or not to require auth credentials */
    public boolean useCredentials = false;

    /** The handler for auth things */
    public AuthReceived authHandler;
}