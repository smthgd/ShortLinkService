package org.example.infra;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.example.core.Link;
import org.example.core.LinkStore;

public final class InMemoryLinkStore implements LinkStore {
    private final ConcurrentMap<String, State> links = new ConcurrentHashMap<>();
    private final Set<UUID> users = ConcurrentHashMap.newKeySet();

    @Override
    public State find(String code) {
        return links.get(code);
    }

    @Override
    public boolean saveIfAbsent(String code, Link link) {
        return links.putIfAbsent(code, link) == null;
    }

    @Override
    public void remove(String code, Link expected) {
        links.remove(code, expected);
    }

    @Override
    public void markExpired(String code, Link expected) {
        links.replace(code, expected, Expired.INSTANCE);
    }

    @Override
    public void removeExpired(Instant now) {
        links.forEach((code, state) -> {
            if (state instanceof Link link && link.isExpiredAt(now)) {
                markExpired(code, link);
            }
        });
    }

    @Override
    public boolean addUser(UUID user) {
        return users.add(user);
    }

    @Override
    public boolean hasUser(UUID user) {
        return users.contains(user);
    }

    @Override
    public void removeUser(UUID user) {
        users.remove(user);
    }
}
