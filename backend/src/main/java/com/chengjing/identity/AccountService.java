package com.chengjing.identity;

import com.chengjing.shared.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Reading and changing the caller's own account (FR-A03, FR-A04).
 *
 * <p>Every method takes an {@link AuthUser} and looks the account up by {@code caller.id()}. No
 * method accepts an account id from the request, so "you can only read and change your own
 * profile" is a property of the shape of this API rather than a check that a later edit could
 * drop. docs/需求分析说明书.md §8 states the same rule for the whole system.
 */
@Service
public class AccountService {
    private final UserStore users;
    private final PasswordEncoder encoder;

    public AccountService(UserStore users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /**
     * The caller's own enabled account.
     *
     * <p>Re-read from the store on every call rather than trusted from the token, so a rename,
     * a sign-out elsewhere or an erasure takes effect immediately.
     */
    public User requireOwnAccount(AuthUser caller) {
        return users.findById(caller.id())
                .filter(User::enabled)
                .orElseThrow(() -> ApiException.unauthorized("账号不存在或已停用"));
    }

    public AccountView profile(AuthUser caller) {
        return AccountView.of(requireOwnAccount(caller));
    }

    /** FR-A03: change the display name. Returns the stored account so the client can re-render. */
    public AccountView rename(AuthUser caller, String displayName) {
        User account = requireOwnAccount(caller);
        return AccountView.of(users.save(account.withDisplayName(displayName.trim())));
    }

    /**
     * FR-A04: set a new password, proving the old one first.
     *
     * <p>Raising {@link User#authVersion()} is what ends the other sessions: every token already
     * issued carries the version it was minted with, and {@link TokenService#parse} refuses a
     * mismatch. The caller's own token is invalidated too — a password change is exactly the moment
     * to stop trusting whatever credential was in the browser.
     *
     * <p>Changing to the current password is refused rather than accepted: it would silently sign
     * the account out of every device without having changed anything.
     */
    public void changePassword(AuthUser caller, String currentPassword, String newPassword) {
        PasswordRules.validate(newPassword);
        User account = requireOwnAccount(caller);
        if (currentPassword == null || !encoder.matches(currentPassword, account.passwordHash())) {
            throw ApiException.badRequest("当前密码不正确");
        }
        if (encoder.matches(newPassword, account.passwordHash())) {
            throw ApiException.badRequest("新密码不能与当前密码相同");
        }
        users.save(account
                .withPasswordHash(encoder.encode(newPassword))
                .withAuthVersionRaised());
    }

    /**
     * FR-A04: end every session on every device without changing the password.
     *
     * <p>Same mechanism as a password change — a new auth version — so a token stolen from another
     * device stops working even though the password itself was not exposed.
     */
    public void logoutEverywhere(AuthUser caller) {
        User account = requireOwnAccount(caller);
        users.save(account.withAuthVersionRaised());
    }
}
