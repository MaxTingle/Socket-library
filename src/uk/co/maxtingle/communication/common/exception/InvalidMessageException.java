package uk.co.maxtingle.communication.common.exception;

import com.sun.istack.internal.Nullable;

/**
 * An exception that is thrown when a message from the client / server
 * fails to parse into from a SerializedMessage object sent to the
 * Message that it needs to be handled as
 */
public class InvalidMessageException extends Exception {

    /**
     * Creates a new instance of the InvalidMessageException
     * and sets the message that caused the exception
     */
    public InvalidMessageException(@Nullable String msg) {
        super(msg);
    }
}