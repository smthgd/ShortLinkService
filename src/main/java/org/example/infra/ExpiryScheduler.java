package org.example.infra;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExpiryScheduler implements AutoCloseable {
    private final ScheduledExecutorService executor;

    public ExpiryScheduler(Runnable cleanup, int intervalSeconds) {
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "expired-link-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(cleanup, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
