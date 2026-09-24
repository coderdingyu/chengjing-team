package com.chengjing.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the short-lived bearer token (FR-A02).
 *
 * <p>The token carries only an account id, a {@code jti}, the account's auth version and the
 * expiry. Name and email are deliberately left out: a token is the one credential most likely to
 * end up in a log or a bug report, so it should reveal nothing about the person holding it. Every
 * request re-reads the account from the store anyway, which is also what makes a password change
 * take effect immediately instead of at the next expiry.
 */
@Service
public class TokenService {
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;
    private final TokenRevocations revocations;
    private final UserStore users;

    public TokenService(
            @Value("${chengjing.identity.jwt.secret}") String secret,
            @Value("${chengjing.identity.jwt.expire-minutes}") long expireMinutes,
            TokenRevocations revocations,
            UserStore users) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            // Fail at startup rather than quietly padding a weak key into something that looks fine.
            throw new IllegalStateException(
                    "chengjing.identity.jwt.secret 至少需要 " + MIN_SECRET_BYTES
                            + " 字节，请通过环境变量 JWT_SECRET 配置一个足够长的随机值");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofMinutes(expireMinutes);
        this.revocations = revocations;
        this.users = users;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.id())
                .claim("version", user.authVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Resolves a token to the account it belongs to, or throws {@link InvalidTokenException}.
     *
     * <p>Checks, in order: signature and expiry, explicit sign-out, the account still existing and
     * being enabled, and the token's auth version still matching the account's.
     */
    public AuthUser parse(String token) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("签名或有效期校验未通过");
        }

        if (revocations.isRevoked(claims.getId())) {
            throw new InvalidTokenException("该令牌已登出");
        }

        User user = users.findById(claims.getSubject())
                .filter(User::enabled)
                .orElseThrow(() -> new InvalidTokenException("账号不存在或已停用"));

        Number version = claims.get("version", Number.class);
        if (user.authVersion() != (version == null ? 0 : version.intValue())) {
            throw new InvalidTokenException("令牌版本已失效");
        }

        return new AuthUser(user.id(), user.email(), user.displayName());
    }

    /**
     * Records an explicit sign-out. A token that is already invalid or expired needs no record —
     * the signature or expiry check rejects it anyway.
     */
    public void revoke(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            revocations.revoke(claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ignored) {
            // Nothing to remember.
        }
    }

    /** Test support: the configured lifetime, so tests can assert on expiry behaviour. */
    public Duration ttl() {
        return ttl;
    }
}
