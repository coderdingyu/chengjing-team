package com.chengjing.identity;

/**
 * A token that must not be trusted: bad signature, wrong shape, expired, signed out, belonging to
 * a disabled account, or issued before the account's current {@link User#authVersion()}.
 *
 * <p>Callers treat every one of those the same way — as "not signed in" — so the reason is
 * deliberately not carried outward. Distinguishing them in a response would tell an attacker
 * whether a token was once valid.
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("token is not valid");
    }

    public InvalidTokenException(String message) {
        super(message);
    }
}
