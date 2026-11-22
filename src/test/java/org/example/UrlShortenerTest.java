package org.example;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlShortenerTest {
    private static final String URL = "https://example.org/article";
    private static final BrowserOpener IGNORE = uri -> {};
    private static final BrowserOpener MUST_NOT_OPEN = uri -> fail("Браузер не должен открываться");

    private final TestClock clock = new TestClock();
    private final UrlShortener service = new UrlShortener(clock, new AppConfig(Duration.ofHours(1), 10, 6, 3600));

    @AfterEach
    void closeService() {
        service.close();
    }

    @Test
    void givesDifferentCodesToDifferentUsersForTheSameUrl() {
        var first = service.shorten(URL, 1, null);
        var second = service.shorten(URL, 1, null);
        assertNotEquals(first.owner(), second.owner());
        assertNotEquals(first.code(), second.code());
    }

    @Test
    void supportsMultipleLinksForOneUser() {
        var first = service.shorten(URL, 1, null);
        var second = service.shorten(URL, 2, first.owner());
        assertEquals(first.owner(), second.owner());
        assertNotEquals(first.code(), second.code());
    }

    @Test
    void opensTheOriginalUrl() throws IOException {
        var link = service.shorten(URL, 2, null);
        assertEquals(new Visit(1, 2), service.follow(link.code(), uri -> assertEquals(URI.create(URL), uri)));
    }

    @Test
    void blocksAttemptsAfterTheLimitAndExplainsWhy() throws IOException {
        var link = service.shorten(URL, 1, null);
        assertTrue(service.follow(link.code(), IGNORE).limitReached());
        var error = assertThrows(IllegalStateException.class, () -> service.follow(link.code(), MUST_NOT_OPEN));
        assertTrue(error.getMessage().startsWith("Лимит переходов исчерпан"));
    }

    @Test
    void doesNotCountFailedBrowserLaunches() throws IOException {
        var link = service.shorten(URL, 1, null);
        assertThrows(IOException.class, () -> service.follow(link.code(), uri -> { throw new IOException(); }));
        assertEquals(new Visit(1, 1), service.follow(link.code(), IGNORE));
    }

    @Test
    void ownerCanRaiseTheLimitWithoutResettingTheCounter() throws IOException {
        var link = service.shorten(URL, 1, null);
        service.follow(link.code(), IGNORE);
        assertEquals(new Visit(1, 2), service.changeVisitLimit(link.code(), link.owner(), 2));
        assertEquals(new Visit(2, 2), service.follow(link.code(), IGNORE));
    }

    @Test
    void rejectsChangesAndDeletionByAnotherUser() throws IOException {
        var link = service.shorten(URL, 2, null);
        UUID otherOwner = service.shorten(URL, 1, null).owner();
        assertThrows(IllegalStateException.class, () -> service.changeVisitLimit(link.code(), otherOwner, 3));
        assertThrows(IllegalStateException.class, () -> service.delete(link.code(), otherOwner));
        assertEquals(new Visit(1, 2), service.follow(link.code(), IGNORE));
    }

    @Test
    void ownerCanDeleteTheLink() {
        var link = service.shorten(URL, 1, null);
        service.delete(link.code(), link.owner());
        var error = assertThrows(IllegalArgumentException.class, () -> service.follow(link.code(), MUST_NOT_OPEN));
        assertEquals("Короткая ссылка не найдена.", error.getMessage());
    }

    @Test
    void rejectsInvalidUrlsAndLimits() {
        assertThrows(IllegalArgumentException.class, () -> service.shorten("ftp://example.org/", 1, null));
        assertThrows(IllegalArgumentException.class, () -> service.shorten(URL, 0, null));
        var link = service.shorten(URL, 1, null);
        assertThrows(IllegalArgumentException.class, () -> service.changeVisitLimit(link.code(), link.owner(), -1));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 24})
    void blocksExactlyAtTheConfiguredExpiry(int hours) throws IOException {
        Duration ttl = Duration.ofHours(hours);
        try (var configured = new UrlShortener(clock, new AppConfig(ttl, 10, 6, 3600))) {
            var link = configured.shorten(URL, 2, null);
            clock.advance(ttl.minusNanos(1));
            assertEquals(new Visit(1, 2), configured.follow(link.code(), IGNORE));
            clock.advance(Duration.ofNanos(1));
            var error = assertThrows(IllegalStateException.class, () -> configured.follow(link.code(), MUST_NOT_OPEN));
            assertTrue(error.getMessage().startsWith("Срок действия ссылки истёк"));
        }
    }

    @Test
    void cleanupDiscardsExpiredLinkDataAndPreservesTheExpiryNotice() {
        var link = service.shorten(URL, 1, null);
        clock.advance(Duration.ofHours(1));
        service.removeExpiredLinks();

        // An earlier clock reading cannot make a link available after cleanup.
        clock.advance(Duration.ofHours(-1));
        var error = assertThrows(IllegalStateException.class, () -> service.follow(link.code(), MUST_NOT_OPEN));
        assertTrue(error.getMessage().startsWith("Срок действия ссылки истёк"));
    }
}
