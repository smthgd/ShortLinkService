package org.example.core;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores users and link state for a session. Conditional updates are atomic.
 * A live entry retains the same Link instance so operations can synchronize on it.
 */
public interface LinkStore {
    sealed interface State permits Link, Expired {
    }

    enum Expired implements State {
        INSTANCE
    }

    State find(String code);

    boolean saveIfAbsent(String code, Link link);

    void remove(String code, Link expected);

    void markExpired(String code, Link expected);

    void removeExpired(Instant now);

    boolean addUser(UUID user);

    boolean hasUser(UUID user);

    void removeUser(UUID user);
}
