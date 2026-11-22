package org.example.core;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

public final class UrlShortener {
    public static final String LIMIT_REACHED = "Лимит переходов исчерпан. Ссылка недоступна. Создайте новую ссылку.";
    public static final String INVALID_LIMIT = "Лимит переходов должен быть положительным целым числом.";
    private static final String LINK_EXPIRED = "Срок действия ссылки истёк. Создайте новую ссылку.";
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int MAX_CODE_ATTEMPTS = 100;

    private final SecureRandom random = new SecureRandom();
    private final LinkStore store;
    private final Clock clock;
    private final Duration linkLifetime;
    private final int codeLength;

    public UrlShortener(LinkStore store, Clock clock, Duration linkLifetime, int codeLength) {
        this.store = store;
        this.clock = clock;
        this.linkLifetime = linkLifetime;
        this.codeLength = codeLength;
    }

    public CreatedLink shorten(String url, int visitLimit, UUID owner) {
        validateLimit(visitLimit);
        URI destination = parseHttpUrl(url);
        boolean newUser = owner == null;
        if (newUser) {
            do {
                owner = UUID.randomUUID();
            } while (!store.addUser(owner));
        } else if (!hasUser(owner)) {
            throw new IllegalArgumentException("Пользователь не найден.");
        }
        var link = new Link(destination, visitLimit, clock.instant().plus(linkLifetime), owner);
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generateCode();
            if (store.saveIfAbsent(code, link)) {
                return new CreatedLink(code, owner);
            }
        }
        if (newUser) {
            store.removeUser(owner);
        }
        throw new IllegalStateException("Не удалось подобрать свободный код. Увеличьте длину кода в настройках.");
    }

    public boolean hasUser(UUID user) {
        return user != null && store.hasUser(user);
    }

    public Visit follow(String code, BrowserOpener browser) throws IOException {
        Link link = findLink(code);
        synchronized (link) {
            requireActiveLink(code, link);
            if (link.visit().limitReached()) {
                throw new IllegalStateException(LIMIT_REACHED);
            }

            browser.open(link.destination());
            return link.recordVisit();
        }
    }

    public Visit changeVisitLimit(String code, UUID owner, int visitLimit) {
        validateLimit(visitLimit);
        Link link = findOwnedLink(code, owner);
        synchronized (link) {
            requireActiveLink(code, link);
            return link.changeLimit(visitLimit);
        }
    }

    public void delete(String code, UUID owner) {
        Link link = findOwnedLink(code, owner);
        synchronized (link) {
            requireActiveLink(code, link);
            store.remove(code, link);
        }
    }

    private Link findLink(String code) {
        return requireLink(store.find(code));
    }

    private Link findOwnedLink(String code, UUID owner) {
        Link link = findLink(code);
        if (!link.owner().equals(owner)) {
            throw new IllegalStateException("Управлять ссылкой может только её создатель.");
        }
        return link;
    }

    private void requireActiveLink(String code, Link link) {
        if (requireLink(store.find(code)) != link) {
            throw new IllegalArgumentException("Короткая ссылка не найдена.");
        }
        if (link.isExpiredAt(clock.instant())) {
            store.markExpired(code, link);
            throw new IllegalStateException(LINK_EXPIRED);
        }
    }

    private static Link requireLink(LinkStore.State state) {
        if (state == LinkStore.Expired.INSTANCE) {
            throw new IllegalStateException(LINK_EXPIRED);
        }
        if (state instanceof Link link) {
            return link;
        }
        throw new IllegalArgumentException("Короткая ссылка не найдена.");
    }

    private static void validateLimit(int visitLimit) {
        if (visitLimit <= 0) {
            throw new IllegalArgumentException(INVALID_LIMIT);
        }
    }

    public void removeExpiredLinks() {
        store.removeExpired(clock.instant());
    }

    private String generateCode() {
        var code = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
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
