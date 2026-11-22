package org.example.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public record AppConfig(Duration linkLifetime, int defaultVisitLimit, int codeLength, int cleanupIntervalSeconds) {
    public static AppConfig load() {
        Path path = Path.of("application.properties");
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            return new AppConfig(
                    Duration.ofSeconds(readInt(properties, "link.ttl.seconds", Integer.MAX_VALUE)),
                    readInt(properties, "link.default-visit-limit", Integer.MAX_VALUE),
                    readInt(properties, "link.code-length", 64),
                    readInt(properties, "cleanup.interval.seconds", Integer.MAX_VALUE)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать файл настроек " + path.toAbsolutePath(), e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Ошибка в " + path + ": " + e.getMessage(), e);
        }
    }

    private static int readInt(Properties properties, String key, int max) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Отсутствует параметр " + key + ".");
        }

        int number;
        try {
            number = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Параметр " + key + " должен быть целым числом.", e);
        }
        if (number < 1 || number > max) {
            throw new IllegalArgumentException("Параметр " + key + " должен быть в диапазоне от 1 до " + max + ".");
        }
        return number;
    }
}
