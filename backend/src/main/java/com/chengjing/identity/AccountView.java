package com.chengjing.identity;

import java.time.Instant;

/**
 * The account as its owner may see it.
 *
 * <p>An explicit allow-list rather than a view over {@link User}: the domain object holds a
 * password hash, and serialising it by accident is exactly the leak this type prevents.
 */
public record AccountView(String id, String email, String displayName, Instant createdAt) {

    public static AccountView of(User user) {
        return new AccountView(
                user.id(), user.email(), user.displayName(), user.createdAt());
    }
}
