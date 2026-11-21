package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class UrlShortener implements AutoCloseable {
    static final int DEFAULT_VISIT_LIMIT = 10;
    static final String LIMIT_REACHED = "Лимит переходов исчерпан. Ссылка недоступна. Создайте новую ссылку.";
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 6;
    private static final Duration LINK_LIFETIME = Duration.ofHours(24);
    private static final long CLEANUP_INTERVAL_SECONDS = 1;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, Link> links = new ConcurrentHashMap<>();
    private final Clock clock;
    private final ScheduledExecutorService cleanup;

    public UrlShortener() {
        this(Clock.systemUTC());
    }

    UrlShortener(Clock clock) {
        this.clock = clock;
        cleanup = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "expired-link-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        cleanup.scheduleWithFixedDelay(this::removeExpiredLinks,
                CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public String shorten(String url) {
        return shorten(url, DEFAULT_VISIT_LIMIT);
    }

    public String shorten(String url, int visitLimit) {
        if (visitLimit <= 0) {
            throw new IllegalArgumentException("Лимит переходов должен быть положительным целым числом.");
        }
        var link = new Link(parseHttpUrl(url), visitLimit, clock.instant().plus(LINK_LIFETIME));
        String code;
        do {
            code = generateCode();
        } while (links.putIfAbsent(code, link) != null);
        return code;
    }

    public Visit follow(String code, BrowserOpener browser) throws IOException {
        Link link = links.get(code);
        if (link == null) {
            throw new IllegalArgumentException("Короткая ссылка не найдена.");
        }

        synchronized (link) {
            if (link.isExpiredAt(clock.instant())) {
                links.remove(code, link);
                throw new IllegalStateException("Срок действия ссылки истёк. Создайте новую ссылку.");
            }
            if (link.visits == link.visitLimit) {
                throw new IllegalStateException(LIMIT_REACHED);
            }

            browser.open(link.destination);
            link.visits++;
            return new Visit(link.visits, link.visitLimit);
        }
    }

    private void removeExpiredLinks() {
        Instant now = clock.instant();
        links.values().removeIf(link -> link.isExpiredAt(now));
    }

    @Override
    public void close() {
        cleanup.shutdownNow();
    }

    private static final class Link {
        private final URI destination;
        private final int visitLimit;
        private final Instant expiresAt;
        private int visits;

        private Link(URI destination, int visitLimit, Instant expiresAt) {
            this.destination = destination;
            this.visitLimit = visitLimit;
            this.expiresAt = expiresAt;
        }

        private boolean isExpiredAt(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private String generateCode() {
        var code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    private static URI parseHttpUrl(String url) {
        String error = "Введите полный URL с протоколом http или https.";
        if (url == null) {
            throw new IllegalArgumentException(error);
        }

        URI destination;
        try {
            destination = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(error, e);
        }

        String scheme = destination.getScheme();
        if (destination.getHost() == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException(error);
        }
        return destination;
    }
}
