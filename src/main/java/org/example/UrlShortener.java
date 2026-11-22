package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class UrlShortener implements AutoCloseable {
    static final String LIMIT_REACHED = "Лимит переходов исчерпан. Ссылка недоступна. Создайте новую ссылку.";
    static final String INVALID_LIMIT = "Лимит переходов должен быть положительным целым числом.";
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int MAX_CODE_ATTEMPTS = 100;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, Link> links = new ConcurrentHashMap<>();
    private final Set<UUID> users = ConcurrentHashMap.newKeySet();
    private final Clock clock;
    private final AppConfig config;
    private final ScheduledExecutorService cleanup;

    public UrlShortener() {
        this(Clock.systemUTC());
    }

    UrlShortener(Clock clock) {
        this.clock = clock;
        config = AppConfig.load();
        cleanup = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "expired-link-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        cleanup.scheduleWithFixedDelay(this::removeExpiredLinks,
                config.cleanupIntervalSeconds(), config.cleanupIntervalSeconds(), TimeUnit.SECONDS);
    }

    public CreatedLink shorten(String url, int visitLimit, UUID owner) {
        validateLimit(visitLimit);
        URI destination = parseHttpUrl(url);
        boolean newUser = owner == null;
        if (newUser) {
            do {
                owner = UUID.randomUUID();
            } while (!users.add(owner));
        } else if (!hasUser(owner)) {
            throw new IllegalArgumentException("Пользователь не найден.");
        }
        var link = new Link(destination, visitLimit, clock.instant().plus(config.linkLifetime()), owner);
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generateCode();
            if (links.putIfAbsent(code, link) == null) {
                return new CreatedLink(code, owner);
            }
        }
        if (newUser) {
            users.remove(owner);
        }
        throw new IllegalStateException("Не удалось подобрать свободный код. Увеличьте длину кода в настройках.");
    }

    int defaultVisitLimit() {
        return config.defaultVisitLimit();
    }

    public boolean hasUser(UUID user) {
        return user != null && users.contains(user);
    }

    public Visit follow(String code, BrowserOpener browser) throws IOException {
        Link link = findLink(code);
        synchronized (link) {
            requireActiveLink(code, link);
            if (link.visits >= link.visitLimit) {
                throw new IllegalStateException(LIMIT_REACHED);
            }

            browser.open(link.destination);
            link.visits++;
            return new Visit(link.visits, link.visitLimit);
        }
    }

    public Visit changeVisitLimit(String code, UUID owner, int visitLimit) {
        validateLimit(visitLimit);
        Link link = findOwnedLink(code, owner);
        synchronized (link) {
            requireActiveLink(code, link);
            link.visitLimit = visitLimit;
            return new Visit(link.visits, link.visitLimit);
        }
    }

    public void delete(String code, UUID owner) {
        Link link = findOwnedLink(code, owner);
        synchronized (link) {
            requireActiveLink(code, link);
            links.remove(code, link);
        }
    }

    private Link findLink(String code) {
        Link link = links.get(code);
        if (link == null) {
            throw new IllegalArgumentException("Короткая ссылка не найдена.");
        }
        return link;
    }

    private Link findOwnedLink(String code, UUID owner) {
        Link link = findLink(code);
        if (!link.owner.equals(owner)) {
            throw new IllegalStateException("Управлять ссылкой может только её создатель.");
        }
        return link;
    }

    private void requireActiveLink(String code, Link link) {
        if (link.isExpiredAt(clock.instant())) {
            links.remove(code, link);
            throw new IllegalStateException("Срок действия ссылки истёк. Создайте новую ссылку.");
        }
        if (links.get(code) != link) {
            throw new IllegalArgumentException("Короткая ссылка не найдена.");
        }
    }

    private static void validateLimit(int visitLimit) {
        if (visitLimit <= 0) {
            throw new IllegalArgumentException(INVALID_LIMIT);
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
        private final Instant expiresAt;
        private final UUID owner;
        private int visitLimit;
        private int visits;

        private Link(URI destination, int visitLimit, Instant expiresAt, UUID owner) {
            this.destination = destination;
            this.visitLimit = visitLimit;
            this.expiresAt = expiresAt;
            this.owner = owner;
        }

        private boolean isExpiredAt(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private String generateCode() {
        var code = new StringBuilder(config.codeLength());
        for (int i = 0; i < config.codeLength(); i++) {
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
