package com.chengjing.identity;

import com.chengjing.shared.ApiException;
import org.springframework.stereotype.Service;

/**
 * Reading and changing the caller's own account (FR-A03).
 *
 * <p>Every method takes an {@link AuthUser} and looks the account up by {@code caller.id()}. No
 * method accepts an account id from the request, so "you can only read and change your own
 * profile" is a property of the shape of this API rather than a check that a later edit could
 * drop. docs/需求分析说明书.md §8 states the same rule for the whole system.
 */
@Service
public class AccountService {
    private final UserStore users;

    public AccountService(UserStore users) {
        this.users = users;
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
}
