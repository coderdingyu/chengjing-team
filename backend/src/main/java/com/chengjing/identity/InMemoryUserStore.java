package com.chengjing.identity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link UserStore}.
 *
 * <p>This is the working implementation until E01 provides durable storage; accounts do not
 * survive a restart yet, and that gap belongs to E01's acceptance, not to A01–A06.
 *
 * <p>Lookups are indexed by normalised email as well as id, so {@link #findByEmail} is O(1) and
 * unique-email enforcement does not depend on scanning.
 */
@Component
public class InMemoryUserStore implements UserStore {
    private final Map<String, User> byId = new ConcurrentHashMap<>();
    private final Map<String, String> idByEmail = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String id = idByEmail.get(Emails.normalise(email));
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public boolean existsByEmail(String email) {
        return idByEmail.containsKey(Emails.normalise(email));
    }

    @Override
    public synchronized User save(User user) {
        String email = Emails.normalise(user.email());
        User previous = byId.get(user.id());
        if (previous != null && !previous.email().equals(email)) {
            idByEmail.remove(previous.email());
        }
        byId.put(user.id(), user);
        idByEmail.put(email, user.id());
        return user;
    }

    /** Test and local-reset support: drops every account. */
    public synchronized void clear() {
        byId.clear();
        idByEmail.clear();
    }
}
