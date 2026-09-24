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
 */
public record User(
        String id,
        String email,
        String passwordHash,
        String displayName,
        Instant createdAt) {

    public User withPasswordHash(String nextHash) {
        return new User(id, email, nextHash, displayName, createdAt);
    }

    public User withDisplayName(String nextDisplayName) {
        return new User(id, email, passwordHash, nextDisplayName, createdAt);
    }
}
