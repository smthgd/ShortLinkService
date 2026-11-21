package org.example;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class UrlShortener {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 6;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, URI> links = new ConcurrentHashMap<>();

    public String shorten(String url) {
        URI destination = parseHttpUrl(url);
        String code;
        do {
            code = generateCode();
        } while (links.putIfAbsent(code, destination) != null);
        return code;
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
