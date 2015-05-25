package uk.co.maxtingle.communication.debug;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

/**
 * A delegate event wrapper for when the log method is
 * called in the Debugger
 */
public interface EventLogger {

    /**
     * The method to call when the Debugger's log method is used
     * assuming the event logger has been assigned as the logger
     * to used
     *
     * @param category The category of the exception, usually a prefix for the message
     * @param msg      The message to log, can be multiple lines via the newline character
     */
    void log(@NotNull String category, @Nullable String msg);
}
