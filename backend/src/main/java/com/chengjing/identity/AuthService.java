package com.chengjing.identity;

import com.chengjing.shared.ApiException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Registration and sign-in use cases (FR-A01, FR-A02).
 *
 * <p>This service owns the account lifecycle rules; controllers only translate HTTP to these calls.
 */
@Service
public class AuthService {
    private final UserStore users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final AccountService accounts;

    /**
     * A hash of a throwaway value, used to keep sign-in timing even when the address is unknown.
     * Without it, "no such account" returns before any hashing work while "wrong password" pays for
     * a BCrypt comparison — a difference an attacker can measure to discover which addresses are
     * registered. Comparing against this hash makes both paths do the same work.
     */
    private final String timingEqualiserHash;

    public AuthService(
            UserStore users, PasswordEncoder encoder, TokenService tokens, AccountService accounts) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.accounts = accounts;
        this.timingEqualiserHash = encoder.encode(UUID.randomUUID().toString());
    }

    /**
     * FR-A01: create an account and sign it in.
     *
     * <p>The password is hashed before it is stored — {@code encoder.encode} is the only thing that
     * ever touches the raw value, and the raw value is never written to the store or to a log.
     */
    public TokenResponse register(String email, String password, String displayName) {
        PasswordRules.validate(password);
        String normalisedEmail = Emails.normalise(email);
        if (users.existsByEmail(normalisedEmail)) {
            throw ApiException.conflict("该邮箱已注册，请直接登录");
        }
        User user = User.newAccount(
                UUID.randomUUID().toString(),
                normalisedEmail,
                encoder.encode(password),
                displayName.trim(),
                Instant.now());
        return signedIn(users.save(user));
    }

    /**
     * FR-A02: exchange a password for a short-lived token.
     *
     * <p>An unknown address and a wrong password answer identically, so the response never reveals
     * whether an account exists.
     */
    public TokenResponse login(String email, String password) {
        Optional<User> found = users.findByEmail(Emails.normalise(email)).filter(User::enabled);
        String hash = found.map(User::passwordHash).orElse(timingEqualiserHash);
        boolean passwordMatches = password != null && encoder.matches(password, hash);
        if (found.isEmpty() || !passwordMatches) {
            throw ApiException.unauthorized("邮箱或密码错误");
        }
        return signedIn(found.get());
    }

    /** FR-A02: end this one session. Other devices signed in to the same account stay signed in. */
    public void logout(String token) {
        tokens.revoke(token);
    }

    /**
     * FR-A03: the caller's own account, re-read from the store so a rename is reflected at once
     * rather than only in tokens issued afterwards.
     *
     * <p>Delegates to {@link AccountService} so "load my own enabled account" has one definition;
     * {@code /auth/me} and {@code /account} therefore cannot drift apart.
     */
    public AccountView me(AuthUser caller) {
        return accounts.profile(caller);
    }

    private TokenResponse signedIn(User user) {
        return new TokenResponse(tokens.issue(user), AccountView.of(user));
    }
}
