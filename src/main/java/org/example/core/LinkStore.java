package org.example.core;

import java.time.Instant;
import java.util.UUID;

/**
 * Хранит пользователей и состояния ссылок в рамках сеанса. Условные изменения выполняются атомарно.
 * Для действующей ссылки хранилище возвращает один и тот же экземпляр {@link Link},
 * чтобы операции могли синхронизироваться на нём.
 */
public interface LinkStore {
    sealed interface State permits Link, Expired {
    }

    enum Expired implements State {
        INSTANCE
    }

    State find(String code);

    boolean saveIfAbsent(String code, Link link);

    void remove(String code, Link expected);

    void markExpired(String code, Link expected);

    void removeExpired(Instant now);

    boolean addUser(UUID user);

    boolean hasUser(UUID user);

    void removeUser(UUID user);
}
