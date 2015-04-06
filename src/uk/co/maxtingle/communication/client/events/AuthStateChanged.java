package uk.co.maxtingle.communication.client.events;

import uk.co.maxtingle.communication.client.AuthState;
import uk.co.maxtingle.communication.client.Client;

public interface AuthStateChanged
{
    void onAuthStateChanged(AuthState previous, AuthState newState, Client client);
}
