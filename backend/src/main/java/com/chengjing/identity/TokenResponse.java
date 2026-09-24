package com.chengjing.identity;

/**
 * What a successful sign-in (or sign-up) hands back: the bearer token and the account it belongs to.
 *
 * <p>The account is returned alongside the token so the client does not have to make a second call
 * just to render the caller's own name.
 */
public record TokenResponse(String token, AccountView user) {
}
