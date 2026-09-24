package com.chengjing.identity;

import java.time.Instant;

/**
 * Account aggregate.
 *
 * <p>Immutable on purpose: every change produces a new instance that {@link UserStore#save}
 * replaces, so a caller holding a reference can never mutate stored state behind the store's back.
 *
 * <p>{@code passwordHash} is a BCrypt digest. It is never returned by an API and never logged
 * (docs/需求分析说明书.md §8, NFR-05).
 *
 * <p>{@code authVersion} is stamped into every issued token. Raising it invalidates all tokens
 * already handed out for this account — that is how a password change and an erasure take effect
 * on sessions that are already open somewhere else.
 */
public record User(
        String id,
        String email,
        String passwordHash,
        String displayName,
        int authVersion,
        boolean enabled,
        Instant createdAt) {

    /** A freshly registered account: no tokens issued yet, so the first session is version 0. */
    public static User newAccount(
            String id, String email, String passwordHash, String displayName, Instant createdAt) {
        return new User(id, email, passwordHash, displayName, 0, true, createdAt);
    }

    public User withPasswordHash(String nextHash) {
        return new User(id, email, nextHash, displayName, authVersion, enabled, createdAt);
    }

    public User withDisplayName(String nextDisplayName) {
        return new User(id, email, passwordHash, nextDisplayName, authVersion, enabled, createdAt);
    }

    /** Invalidates every token already issued for this account. */
    public User withAuthVersionRaised() {
        return new User(id, email, passwordHash, displayName, authVersion + 1, enabled, createdAt);
    }

    /**
     * Disabling an account stops it signing in and stops its existing tokens resolving, because
     * {@link TokenService#parse} filters on {@link #enabled()}. Used by erasure (A06).
     */
    public User withEnabled(boolean nextEnabled) {
        return new User(id, email, passwordHash, displayName, authVersion, nextEnabled, createdAt);
    }
}
