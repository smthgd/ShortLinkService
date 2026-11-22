package org.example;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Scanner;
import org.example.cli.Console;
import org.example.core.UrlShortener;
import org.example.infra.AppConfig;
import org.example.infra.DesktopBrowser;
import org.example.infra.ExpiryScheduler;
import org.example.infra.InMemoryLinkStore;

public class Main {
    public static void main(String[] args) {
        try (var input = new Scanner(System.in, StandardCharsets.UTF_8)) {
            var config = AppConfig.load();
            var service = new UrlShortener(new InMemoryLinkStore(), Clock.systemUTC(),
                    config.linkLifetime(), config.codeLength());
            try (var cleanup = new ExpiryScheduler(service::removeExpiredLinks, config.cleanupIntervalSeconds())) {
                Console.run(input, System.out, service, new DesktopBrowser(), config.defaultVisitLimit());
            }
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
