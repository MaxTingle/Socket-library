package uk.co.maxtingle.communication.common.exception;

import com.sun.istack.internal.Nullable;

/**
 * An exception class that is fired when the user
 * has failed to authenticate on the server
 */
public class AuthException extends Exception {

    /**
     * Creates a new instance of the auth exception and
     * sets the message that caused the exception
     */
    public AuthException(@Nullable String msg) {
        super(msg);
    }
}
