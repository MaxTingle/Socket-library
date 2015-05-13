package uk.co.maxtingle.communication.common.events;

import uk.co.maxtingle.communication.common.AuthState;
import uk.co.maxtingle.communication.common.BaseClient;

public interface AuthStateChanged
{
    void onAuthStateChanged(AuthState previous, AuthState newState, BaseClient client);
}
