package uk.co.maxtingle.communication.common.events;

import com.sun.istack.internal.NotNull;
import uk.co.maxtingle.communication.common.AuthState;
import uk.co.maxtingle.communication.common.BaseClient;

/**
 * A delegate wrapper for the auth state changed event
 * Fired when a client's auth state has changed
 */
public interface AuthStateChanged
{
    /**
     * Fired when the auth state of a client changes. Whether that be a client
     * receiving an update from the server on its auth state or the server
     * setting the auth state of the client
     *
     * @param previous The auth state of the client before it was updated
     * @param newState The auth state of the client after it was updated
     * @param client   The client whose auth state has changed
     */
    void onAuthStateChanged(@NotNull AuthState previous, @NotNull AuthState newState, @NotNull BaseClient client);
}
