package org.example;

import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        try (var input = new Scanner(System.in, StandardCharsets.UTF_8)) {
            run(input, Main::openBrowser);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    static void run(Scanner input, BrowserOpener browser) {
        try (var shortener = new UrlShortener()) {
            run(input, browser, shortener, System.out);
        }
    }

    static void run(Scanner input, BrowserOpener browser, UrlShortener shortener, PrintStream output) {
        UUID currentUser = null;
        output.println("Здравствуйте! Доступные функции:\n" +
                "   Введите URL для сокращения\n" +
                "   go <код> для перехода\n" +
                "   user <UUID> для выбора пользователя\n" +
                "   user new для начала работы без выбранного пользователя\n" +
                "   edit <код> <лимит> для изменения лимита своей ссылки\n" +
                "   delete <код> для удаления своей ссылки\n" +
                "   exit для выхода\n");

        while (input.hasNextLine()) {
            String line = input.nextLine().strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] command = line.split("\\s+");
            try {
                switch (command[0].toLowerCase(Locale.ROOT)) {
                    case "exit" -> {
                        if (command.length == 1) {
                            return;
                        }
                        output.println("Использование: exit");
                    }
                    case "go" -> {
                        requireArguments(command, 2, "go <код>");
                        printVisit(output, shortener.follow(shortCode(command[1]), browser));
                    }
                    case "user" -> {
                        requireArguments(command, 2, "user <UUID> или user new");
                        currentUser = selectUser(output, shortener, command[1]);
                    }
                    case "edit" -> {
                        requireArguments(command, 3, "edit <код> <лимит>");
                        Visit visit = shortener.changeVisitLimit(shortCode(command[1]), currentUser,
                                Integer.parseInt(command[2]));
                        output.println("Лимит обновлён.");
                        printVisit(output, visit);
                    }
                    case "delete" -> {
                        requireArguments(command, 2, "delete <код>");
                        shortener.delete(shortCode(command[1]), currentUser);
                        output.println("Ссылка удалена.");
                    }
                    default -> {
                        output.printf("Введите лимит переходов (Enter — %d).%n",
                                shortener.defaultVisitLimit());
                        if (!input.hasNextLine()) {
                            return;
                        }
                        String limit = input.nextLine().strip();
                        if ("exit".equalsIgnoreCase(limit)) {
                            return;
                        }
                        int visitLimit = limit.isEmpty() ? shortener.defaultVisitLimit()
                                : Integer.parseInt(limit);
                        CreatedLink link = shortener.shorten(line, visitLimit, currentUser);
                        if (currentUser == null) {
                            currentUser = link.owner();
                            output.println("Ваш UUID: " + currentUser);
                        }
                        output.println("Короткая ссылка: /" + link.code());
                    }
                }
            } catch (NumberFormatException e) {
                output.println(UrlShortener.INVALID_LIMIT);
            } catch (IllegalArgumentException | IllegalStateException e) {
                output.println(e.getMessage());
            } catch (IOException e) {
                output.println("Не удалось открыть браузер. Переход не засчитан.");
            }
        }
    }

    private static void requireArguments(String[] command, int expectedLength, String usage) {
        if (command.length != expectedLength) {
            throw new IllegalArgumentException("Использование: " + usage);
        }
    }

    private static UUID selectUser(PrintStream output, UrlShortener shortener, String argument) {
        if ("new".equalsIgnoreCase(argument)) {
            output.println("Новый UUID будет выдан при создании ссылки.");
            return null;
        }
        UUID user;
        try {
            user = UUID.fromString(argument);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Введите UUID в стандартном формате.", e);
        }
        if (!user.toString().equalsIgnoreCase(argument)) {
            throw new IllegalArgumentException("Введите UUID в стандартном формате.");
        }
        if (!shortener.hasUser(user)) {
            throw new IllegalArgumentException("Пользователь не найден.");
        }
        output.println("Текущий пользователь: " + user);
        return user;
    }

    private static String shortCode(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private static void printVisit(PrintStream output, Visit visit) {
        output.printf("Переходы: %d из %d.%n", visit.count(), visit.limit());
        if (visit.limitReached()) {
            output.println(UrlShortener.LIMIT_REACHED);
        }
    }

    private static void openBrowser(URI destination) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Открытие браузера недоступно.");
        }
        Desktop.getDesktop().browse(destination);
    }
}
