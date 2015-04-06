package uk.co.maxtingle.communication;

public class Debugger
{
    private static EventLogger _logger;

    public static void setDefaultLogger() {
        Debugger.setLogger(new EventLogger()
        {
            @Override
            public void log(String category, String msg) {
                System.out.println("[" + category + "] " + msg);
            }
        });
    }

    public static void setLogger(EventLogger logger) {
        Debugger._logger = logger;
    }

    public static void log(String category, String msg) {
        if(_logger != null) {
            _logger.log(category, msg);
        }
    }
}
