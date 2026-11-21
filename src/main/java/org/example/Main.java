package org.example;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try (var input = new Scanner(System.in, StandardCharsets.UTF_8)) {
            run(input, Main::openBrowser);
        }
    }

    static void run(Scanner input, BrowserOpener browser) {
        var shortener = new UrlShortener();

        System.out.println("Здравствуйте! Доступные функции:" +
                "   Введите URL для сокращения" +
                "   go <код> для перехода" +
                "   exit для выхода");

        while (input.hasNextLine()) {
            String line = input.nextLine().strip();
            String[] command = line.split("\\s+", 2);
            if ("exit".equalsIgnoreCase(line)) {
                return;
            }

            try {
                if ("go".equalsIgnoreCase(command[0])) {
                    follow(shortener, command, browser);
                    continue;
                }

                System.out.printf("Введите лимит переходов (Enter — %d).%n", UrlShortener.DEFAULT_VISIT_LIMIT);
                if (!input.hasNextLine()) {
                    return;
                }
                String limit = input.nextLine().strip();
                if ("exit".equalsIgnoreCase(limit)) {
                    return;
                }
                String code = limit.isEmpty()
                        ? shortener.shorten(line)
                        : shortener.shorten(line, Integer.parseInt(limit));
                System.out.println("Короткая ссылка: /" + code);
            } catch (NumberFormatException e) {
                System.out.println("Лимит переходов должен быть положительным целым числом.");
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println(e.getMessage());
            } catch (IOException e) {
                System.out.println("Не удалось открыть браузер. Переход не засчитан.");
            }
        }
    }

    private static void follow(UrlShortener shortener, String[] command, BrowserOpener browser)
            throws IOException {
        if (command.length < 2) {
            throw new IllegalArgumentException("Укажите короткий код после команды go.");
        }
        String code = command[1].strip();
        if (code.startsWith("/")) {
            code = code.substring(1);
        }
        Visit visit = shortener.follow(code, browser);
        System.out.printf("Переходы: %d из %d.%n", visit.count(), visit.limit());
        if (visit.limitReached()) {
            System.out.println(UrlShortener.LIMIT_REACHED);
        }
    }

    private static void openBrowser(URI destination) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Открытие браузера недоступно.");
        }
        Desktop.getDesktop().browse(destination);
    }
}
