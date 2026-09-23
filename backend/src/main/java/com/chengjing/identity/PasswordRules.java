package com.chengjing.identity;

import com.chengjing.shared.ApiException;
import java.nio.charset.StandardCharsets;

/**
 * Password policy for registration and password changes.
 *
 * <p>Length is checked in characters so the rule reads the same to the person typing it, and then
 * in UTF-8 bytes because BCrypt only hashes the first 72 bytes: without the second check a
 * passphrase of Chinese characters would be silently truncated, so two different passwords could
 * open the same account. Rejecting is the safe answer — never truncate a credential.
 */
public final class PasswordRules {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    /** BCrypt's input limit. */
    private static final int MAX_BYTES = 72;

    private PasswordRules() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw ApiException.badRequest(
                    "密码需为 " + MIN_LENGTH + " 至 " + MAX_LENGTH + " 位");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw ApiException.badRequest("密码包含较多多字节字符，请缩短后重试");
        }
    }
}
