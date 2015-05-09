package uk.co.maxtingle.communication.common;

public class InvalidMessageException extends Exception {
    public InvalidMessageException(String msg) {
        super(msg);
    }
}