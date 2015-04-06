package uk.co.maxtingle.communication.client;

public enum AuthState
{
    CONNECTED, //connected via listener
    AWAITING_MAGIC, //asked for the expectedMagic
    AWAITING_CREDENTIALS, //asked for login info
    ACCEPTED //accepted
}
