package org.example;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MainTest {
    private static final String URL = "https://example.org/article";
    private final TestClock clock = new TestClock();
    private final UrlShortener service = new UrlShortener(clock, new AppConfig(Duration.ofHours(1), 4, 8, 3600));

    @AfterEach
    void closeService() {
        service.close();
    }

    @Test
    void createsLinksForDifferentUsersUsingTheDefaultLimit() throws IOException {
        String output = run(URL + "\n\nuser new\n" + URL + "\n\nexit\n", uri -> fail());
        var codes = output.lines().filter(line -> line.matches("Короткая ссылка: /[A-Za-z0-9]{8}")).toList();
        assertEquals(2, codes.size());
        assertNotEquals(codes.get(0), codes.get(1));
        assertEquals(2, output.lines().filter(line -> line.startsWith("Ваш UUID: ")).count());
        assertTrue(output.contains("Enter — 4"));
        String firstCode = codes.getFirst().substring("Короткая ссылка: /".length());
        assertEquals(new Visit(1, 4), service.follow(firstCode, uri -> {}));
    }

    @Test
    void opensTheBrowserOnceAndPrintsTheLimitNotice() {
        var link = service.shorten(URL, 1, null);
        var opened = new ArrayList<URI>();
        String output = run("go /%s\ngo %s\nexit\n".formatted(link.code(), link.code()), opened::add);

        assertEquals(List.of(URI.create(URL)), opened);
        assertTrue(output.contains("Переходы: 1 из 1"));
        assertEquals(2, output.lines().filter(line -> line.startsWith("Лимит переходов исчерпан")).count());
    }

    @Test
    void printsTheExpiryNoticeAfterCleanup() {
        var link = service.shorten(URL, 1, null);
        clock.advance(Duration.ofHours(1));
        service.removeExpiredLinks();

        String output = run("go /%s\nexit\n".formatted(link.code()), uri -> fail());
        assertTrue(output.contains("Срок действия ссылки истёк"));
    }

    @Test
    void requiresTheOwnerForChangesAndDeletion() {
        var link = service.shorten(URL, 1, null);
        var other = service.shorten(URL, 1, null);
        String output = run("""
                user %s
                edit %s 2
                delete %s
                user %s
                edit %s 2
                delete %s
                go %s
                exit
                """.formatted(other.owner(), link.code(), link.code(), link.owner(), link.code(), link.code(), link.code()),
                uri -> fail());

        assertEquals(2, output.lines().filter(line -> line.contains("только её создатель")).count());
        assertTrue(output.contains("Лимит обновлён."));
        assertTrue(output.contains("Переходы: 0 из 2"));
        assertTrue(output.contains("Ссылка удалена."));
        assertTrue(output.contains("Короткая ссылка не найдена."));
    }

    @Test
    void reportsBrowserFailureAndAllowsARetry() {
        var link = service.shorten(URL, 1, null);
        var opened = new ArrayList<URI>();
        String output = run("go %s\ngo %s\nexit\n".formatted(link.code(), link.code()), uri -> {
            opened.add(uri);
            if (opened.size() == 1) {
                throw new IOException();
            }
        });

        assertEquals(List.of(URI.create(URL), URI.create(URL)), opened);
        assertTrue(output.contains("Не удалось открыть браузер. Переход не засчитан."));
        assertTrue(output.contains("Переходы: 1 из 1"));
    }

    private String run(String script, BrowserOpener browser) {
        var bytes = new ByteArrayOutputStream();
        try (var input = new Scanner(script); var output = new PrintStream(bytes, false, UTF_8)) {
            Main.run(input, browser, service, output);
        }
        return bytes.toString(UTF_8);
    }
}
