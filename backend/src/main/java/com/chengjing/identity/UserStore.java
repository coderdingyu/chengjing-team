package com.chengjing.identity;

import java.util.Optional;

/**
 * Account persistence port.
 *
 * <p>Feature modules depend on this interface, never on a storage technology. The skeleton has no
 * database yet — E01 owns "本机持久化基础与迁移" — so A01 ships {@link InMemoryUserStore}. When E01
 * lands, only the implementation behind this port changes; {@link AuthService} and the account
 * services stay as they are.
 *
 * <p>Email is the account key the user types, so implementations must treat it case-insensitively
 * and store the normalised form. {@link #findByEmail} therefore expects an already-normalised
 * address; {@link Emails#normalise} produces one.
 */
public interface UserStore {

    Optional<User> findById(String id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Inserts or replaces by {@link User#id()}. Returns the stored value. */
    User save(User user);
}
