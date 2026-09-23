package com.chengjing.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing.
 *
 * <p>BCrypt with its default strength: a salted, deliberately slow one-way hash. Stored passwords
 * are never reversible, and the same password hashed twice yields different digests, so the store
 * holds no plaintext and no shared-prefix fingerprint (NFR-01).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
