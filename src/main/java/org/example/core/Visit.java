package org.example.core;

public record Visit(int count, int limit) {
    public boolean limitReached() {
        return count >= limit;
    }
}
