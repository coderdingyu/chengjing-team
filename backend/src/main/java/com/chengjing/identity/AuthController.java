package com.chengjing.identity;

import com.chengjing.shared.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and sign-in endpoints (docs/模块契约.md: prefix {@code /api/v1/auth}).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Password length is deliberately absent here: {@link PasswordRules} is the single authority on
     * the policy, so the rule and its message cannot drift between two places.
     *
     * <p>The compact constructor trims before validation runs. Jackson builds the record through its
     * canonical constructor, so a pasted " ada@example.com " is validated and stored as
     * "ada@example.com" instead of being rejected as a malformed address.
     */
    public record RegisterRequest(
            @NotBlank(message = "请填写邮箱")
            @Email(message = "请填写有效的邮箱地址")
            @Size(max = 190, message = "邮箱地址过长")
            String email,

            @NotBlank(message = "请填写密码")
            String password,

            @NotBlank(message = "请填写称呼")
            @Size(max = 80, message = "称呼最多 80 个字")
            String displayName) {

        public RegisterRequest {
            email = email == null ? null : email.trim();
            displayName = displayName == null ? null : displayName.trim();
        }
    }

    /** Login takes no length rule on the password: an old password must still be accepted as typed. */
    public record LoginRequest(
            @NotBlank(message = "请填写邮箱")
            @Email(message = "请填写有效的邮箱地址")
            @Size(max = 190, message = "邮箱地址过长")
            String email,

            @NotBlank(message = "请填写密码")
            String password) {

        public LoginRequest {
            email = email == null ? null : email.trim();
        }
    }

    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(
                request.email(), request.password(), request.displayName()));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.email(), request.password()));
    }

    /**
     * Signs out the session that is calling. The token arrives as the authentication's credentials
     * (see {@link JwtAuthFilter}), which is exactly the session to end.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        Object credentials = authentication == null ? null : authentication.getCredentials();
        authService.logout(credentials instanceof String token ? token : null);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<AccountView> me(@CurrentUser AuthUser caller) {
        return ApiResponse.ok(authService.me(caller));
    }
}
