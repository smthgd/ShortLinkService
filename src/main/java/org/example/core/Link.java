package org.example.core;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public final class Link implements LinkStore.State {
    private final URI destination;
    private final Instant expiresAt;
    private final UUID owner;
    private int visitLimit;
    private int visits;

    Link(URI destination, int visitLimit, Instant expiresAt, UUID owner) {
        this.destination = destination;
        this.visitLimit = visitLimit;
        this.expiresAt = expiresAt;
        this.owner = owner;
    }

    URI destination() {
        return destination;
    }

    UUID owner() {
        return owner;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    Visit visit() {
        return new Visit(visits, visitLimit);
    }

    Visit recordVisit() {
        visits++;
        return visit();
    }

    Visit changeLimit(int limit) {
        visitLimit = limit;
        return visit();
    }
}
