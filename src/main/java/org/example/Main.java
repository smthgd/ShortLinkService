package org.example;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        try (var input = new Scanner(System.in, StandardCharsets.UTF_8)) {
            run(input, Main::openBrowser);
        }
    }

    static void run(Scanner input, BrowserOpener browser) {
        try (var shortener = new UrlShortener()) {
            UUID currentUser = null;
            System.out.println("Здравствуйте! Доступные функции:\n" +
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
                            System.out.println("Использование: exit");
                        }
                        case "go" -> {
                            requireArguments(command, 2, "go <код>");
                            printVisit(shortener.follow(shortCode(command[1]), browser));
                        }
                        case "user" -> {
                            requireArguments(command, 2, "user <UUID> или user new");
                            currentUser = selectUser(shortener, command[1]);
                        }
                        case "edit" -> {
                            requireArguments(command, 3, "edit <код> <лимит>");
                            Visit visit = shortener.changeVisitLimit(shortCode(command[1]), currentUser,
                                    Integer.parseInt(command[2]));
                            System.out.println("Лимит обновлён.");
                            printVisit(visit);
                        }
                        case "delete" -> {
                            requireArguments(command, 2, "delete <код>");
                            shortener.delete(shortCode(command[1]), currentUser);
                            System.out.println("Ссылка удалена.");
                        }
                        default -> {
                            System.out.printf("Введите лимит переходов (Enter — %d).%n",
                                    UrlShortener.DEFAULT_VISIT_LIMIT);
                            if (!input.hasNextLine()) {
                                return;
                            }
                            String limit = input.nextLine().strip();
                            if ("exit".equalsIgnoreCase(limit)) {
                                return;
                            }
                            int visitLimit = limit.isEmpty() ? UrlShortener.DEFAULT_VISIT_LIMIT
                                    : Integer.parseInt(limit);
                            CreatedLink link = shortener.shorten(line, visitLimit, currentUser);
                            if (currentUser == null) {
                                currentUser = link.owner();
                                System.out.println("Ваш UUID: " + currentUser);
                            }
                            System.out.println("Короткая ссылка: /" + link.code());
                        }
                    }
                } catch (NumberFormatException e) {
                    System.out.println(UrlShortener.INVALID_LIMIT);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
                    System.out.println("Не удалось открыть браузер. Переход не засчитан.");
                }
            }
        }
    }

    private static void requireArguments(String[] command, int expectedLength, String usage) {
        if (command.length != expectedLength) {
            throw new IllegalArgumentException("Использование: " + usage);
        }
    }

    private static UUID selectUser(UrlShortener shortener, String argument) {
        if ("new".equalsIgnoreCase(argument)) {
            System.out.println("Новый UUID будет выдан при создании ссылки.");
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
        System.out.println("Текущий пользователь: " + user);
        return user;
    }

    private static String shortCode(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private static void printVisit(Visit visit) {
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
