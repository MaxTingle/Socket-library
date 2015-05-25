package uk.co.maxtingle.communication.server.auth;

/**
 * A class that implements both magic basic and credential
 * based authentication
 */
public interface IAuthHandler extends IMagicAuth, ICredentialAuth {}
