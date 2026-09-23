package com.chengjing.identity;

import java.util.Locale;

/**
 * Email normalisation.
 *
 * <p>"Ada@Example.com " and "ada@example.com" are the same account. Normalising once, here, keeps
 * uniqueness, lookup and login consistent — a store must never have to guess.
 */
public final class Emails {
    private Emails() {
    }

    public static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
