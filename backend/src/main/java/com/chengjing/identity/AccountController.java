package com.chengjing.identity;

import com.chengjing.shared.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own account (docs/模块契约.md: prefix {@code /api/v1/account}).
 *
 * <p>There is deliberately no {@code /account/{id}} route. The account acted on is always the one
 * the bearer token resolves to, which is what keeps another user's profile out of reach.
 * {@code /api/v1/account/**} requires authentication — see {@link IdentitySecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    public record RenameRequest(
            @NotBlank(message = "请填写称呼")
            @Size(max = 80, message = "称呼最多 80 个字")
            String displayName) {

        public RenameRequest {
            displayName = displayName == null ? null : displayName.trim();
        }
    }

    @GetMapping
    public ApiResponse<AccountView> profile(@CurrentUser AuthUser caller) {
        return ApiResponse.ok(accountService.profile(caller));
    }

    @PutMapping
    public ApiResponse<AccountView> rename(
            @CurrentUser AuthUser caller, @Valid @RequestBody RenameRequest request) {
        return ApiResponse.ok(accountService.rename(caller, request.displayName()));
    }

    /**
     * As with login, the current password is not length-checked here: an old password must be
     * accepted exactly as it was typed. {@link PasswordRules} governs the new one.
     */
    public record ChangePasswordRequest(
            @NotBlank(message = "请填写当前密码")
            String currentPassword,

            @NotBlank(message = "请填写新密码")
            String newPassword) {
    }

    /** FR-A04: on success every token for this account, including the caller's, is now invalid. */
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(
            @CurrentUser AuthUser caller, @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(caller, request.currentPassword(), request.newPassword());
        return ApiResponse.ok(null);
    }

    /** FR-A04: end every session on every device, without changing the password. */
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutEverywhere(@CurrentUser AuthUser caller) {
        accountService.logoutEverywhere(caller);
        return ApiResponse.ok(null);
    }

    /**
     * FR-A05: download a copy of your own data.
     *
     * <p>Returns the document in the shared envelope rather than as a file attachment, so the
     * client decides how to save it and the response is still readable in a browser or a test.
     */
    @GetMapping("/export")
    public ApiResponse<AccountExport> export(@CurrentUser AuthUser caller) {
        return ApiResponse.ok(accountService.export(caller));
    }
}
