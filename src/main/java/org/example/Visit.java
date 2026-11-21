package org.example;

public record Visit(int count, int limit) {
    public boolean limitReached() {
        return count >= limit;
    }
}
