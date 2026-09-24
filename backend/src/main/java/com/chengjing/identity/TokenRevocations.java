package com.chengjing.identity;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tokens that were explicitly signed out before their expiry.
 *
 * <p>Keyed by the token's own {@code jti}, so signing out ends exactly the session that asked for
 * it and leaves the account's other devices alone (FR-A02). Raising {@link User#authVersion()} is
 * the blunt instrument used when *every* session must end; this is the precise one.
 *
 * <p>An entry only matters until the token it names would have expired anyway, so expired entries
 * are dropped on lookup and swept in bulk once the map grows. Like the account store, this is
 * in-memory: a restart forgets explicit sign-outs, while password-change invalidation survives
 * because it lives on the account itself.
 */
@Component
public class TokenRevocations {
    private static final int SWEEP_THRESHOLD = 1024;

    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null || expiresAt == null) {
            return;
        }
        if (revokedUntil.size() >= SWEEP_THRESHOLD) {
            sweep();
        }
        revokedUntil.put(tokenId, expiresAt);
    }

    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        Instant expiresAt = revokedUntil.get(tokenId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            // The token is past its own expiry, so the signature check already rejects it.
            revokedUntil.remove(tokenId);
            return false;
        }
        return true;
    }

    private void sweep() {
        Instant now = Instant.now();
        revokedUntil.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    /** Test and local-reset support. */
    public void clear() {
        revokedUntil.clear();
    }
}
