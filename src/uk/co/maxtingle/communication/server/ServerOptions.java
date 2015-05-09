package uk.co.maxtingle.communication.server;

import uk.co.maxtingle.communication.server.auth.IAuthHandler;
import uk.co.maxtingle.communication.server.auth.ICredentialAuth;
import uk.co.maxtingle.communication.server.auth.IMagicAuth;

public class ServerOptions
{
    /** Reserved strings used by the server and client for authentication */
    public static final String REQUEST_MAGIC       = "__SEND_MAGIC__";
    public static final String REQUEST_CREDENTIALS = "__SEND_CREDENTIALS__";
    public static final String ACCEPTED_AUTH       = "__AUTHENTICATED__";
    public static final String SEND_MAGIC          = "__MAGIC__";
    public static final String SEND_CREDENTIALS    = "__CREDENTIALS__";

    /** Heart options */
    public static final String HEART_BEAT          = "__HEART_BEAT__";

    /**
     * Number of milliseconds between beats of the heart
     */
    public static long         HEART_BMP           = 60 * 1000;


    /** the port to broadcast on */
    public int port = 8080;

    /** whether or not a Client should be told to keep all sent and received messages */
    public boolean keepMessages = true;

    /** whether or not to auth with expectedMagic first */
    public boolean useMagic = false;

    /** the expectedMagic to auth with */
    public String expectedMagic = "";

    /** the expected credentials to auth with */
    public String expectedUsername = "";
    public String expectedPassword = "";

    /** whether or not to require auth credentials */
    public boolean useCredentials = false;

    /** Authenticators */
    IAuthHandler    usedAuthHandler;
    ICredentialAuth credentialAuthHandler;
    IMagicAuth      magicAuthHandler;
}