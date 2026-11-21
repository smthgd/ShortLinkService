package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class UrlShortener {
    static final int DEFAULT_VISIT_LIMIT = 10;
    static final String LIMIT_REACHED = "Лимит переходов исчерпан. Ссылка недоступна. Создайте новую ссылку.";
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 6;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, Link> links = new ConcurrentHashMap<>();

    public String shorten(String url) {
        return shorten(url, DEFAULT_VISIT_LIMIT);
    }

    public String shorten(String url, int visitLimit) {
        if (visitLimit <= 0) {
            throw new IllegalArgumentException("Лимит переходов должен быть положительным целым числом.");
        }
        var link = new Link(parseHttpUrl(url), visitLimit);
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
            if (link.visits == link.visitLimit) {
                throw new IllegalStateException(LIMIT_REACHED);
            }

            browser.open(link.destination);
            link.visits++;
            return new Visit(link.visits, link.visitLimit);
        }
    }

    private static final class Link {
        private final URI destination;
        private final int visitLimit;
        private int visits;

        private Link(URI destination, int visitLimit) {
            this.destination = destination;
            this.visitLimit = visitLimit;
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
