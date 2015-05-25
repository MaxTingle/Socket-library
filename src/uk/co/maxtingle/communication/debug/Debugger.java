package uk.co.maxtingle.communication.debug;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

/**
 * A basic debugger for all the clients and servers
 * that are currently using this instance of the library
 */
public class Debugger
{
    private static EventLogger _logger;

    /**
     * Sets the default logger up so the messages
     * logged will actually get outputted.
     *
     * System defaults to printing to System.out
     */
    public static void setDefaultLogger() {
        Debugger.setLogger(new EventLogger()
        {
            @Override
            public void log(String category, String msg) {
                System.out.println("[" + category + "] " + msg);
            }
        });
    }

    /**
     * Sets the logger to use for outputting things from this
     * library
     *
     * @param logger The logger to use
     */
    public static void setLogger(@NotNull EventLogger logger) {
        Debugger._logger = logger;
    }

    /**
     * Logs something using the set logger, nothing will be
     * logged if the logger has not been setup using setLogger
     * or setDefaultLogger
     *
     * @param category The category of the log, currently used are ServerClient, Client and Server.
     *                 It is recommended you use App.
     * @param msg      The message to log
     */
    public static void log(@NotNull String category, @Nullable String msg) {
        if(_logger != null) {
            _logger.log(category, msg);
        }
    }

    /**
     * Logs an exception using the logger if it is setup
     * will convert the exception to a string using its
     * toString method
     *
     * @param category The category of the log, currently used are ServerClient, Client and Server.
     *                 It is recommended you use App.
     * @param e        The exception to log
     */
    public static void log(@NotNull String category, @NotNull Exception e) {
        Debugger.log(category, e.toString());
    }

    /**
     * Debugs an exception by printing out a full stack trace
     * for the exception to the setup debug logger if it is
     * setup at all
     *
     * @param category The category of the log, currently used are ServerClient, Client and Server.
     *                 It is recommended you use App.
     * @param e        The exception to debug
     */
    public static void debug(String category, Exception e) {
        String stackTrace = "";

        for(StackTraceElement stack : e.getStackTrace()) {
            stackTrace += "\n in " + stack.getClassName() + " ( " + stack.getFileName() + ") " + stack.getMethodName() + " @ " + stack.getLineNumber();
        }

        Debugger.log(category, e.toString() + " stack: " + stackTrace);
    }
}
