package org.example;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        var shortener = new UrlShortener();

        System.out.println("Введите URL для сокращения или exit для выхода.");
        try (var input = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (input.hasNextLine()) {
                String line = input.nextLine().strip();
                if ("exit".equalsIgnoreCase(line)) {
                    return;
                }

                try {
                    System.out.println("Короткая ссылка: /" + shortener.shorten(line));
                } catch (IllegalArgumentException e) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }
}
