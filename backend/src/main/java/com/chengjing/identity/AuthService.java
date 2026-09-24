package com.chengjing.identity;

import com.chengjing.shared.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Registration and sign-in use cases.
 *
 * <p>FR-A01 / FR-A02. This service owns the account lifecycle rules; controllers only translate
 * HTTP to these calls.
 */
@Service
public class AuthService {
    private final UserStore users;
    private final PasswordEncoder encoder;

    public AuthService(UserStore users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /**
     * FR-A01: create a candidate account.
     *
     * <p>The password is hashed before it is stored — {@code encoder.encode} is the only thing that
     * ever touches the raw value, and the raw value is never written to the store or to a log.
     */
    public AccountView register(String email, String password, String displayName) {
        PasswordRules.validate(password);
        String normalisedEmail = Emails.normalise(email);
        if (users.existsByEmail(normalisedEmail)) {
            throw ApiException.conflict("该邮箱已注册，请直接登录");
        }
        User user = new User(
                UUID.randomUUID().toString(),
                normalisedEmail,
                encoder.encode(password),
                displayName.trim(),
                Instant.now());
        return AccountView.of(users.save(user));
    }
}
