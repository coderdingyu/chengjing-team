package com.chengjing.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chengjing.app.ChengjingApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-A01 acceptance: valid input creates an account; a duplicate email and a weak password each
 * fail with a message the user can act on; the stored account holds no plaintext password.
 *
 * <p>{@code classes} is explicit because {@code ChengjingApplication} lives in {@code com.chengjing.app},
 * a sibling of this module's package, so the default upward search cannot find it.
 */
@SpringBootTest(classes = ChengjingApplication.class)
@AutoConfigureMockMvc
class A01RegistrationTest {
    private static final String PASSWORD = "Chengjing-2026";

    @Autowired MockMvc mvc;
    @Autowired InMemoryUserStore users;
    @Autowired AuthService authService;

    @BeforeEach
    void resetStore() {
        users.clear();
    }

    private String body(String email, String password, String displayName) {
        return """
                {"email":"%s","password":"%s","displayName":"%s"}
                """.formatted(email, password, displayName);
    }

    @Test
    @DisplayName("合法输入创建账号并返回账号资料")
    void createsAccountForValidInput() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ada@example.com", PASSWORD, "Ada")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.id").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("ada@example.com"))
                .andExpect(jsonPath("$.data.user.displayName").value("Ada"))
                .andExpect(jsonPath("$.data.user.createdAt").isNotEmpty())
                // A02 extends registration to sign the new account in straight away.
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        assertThat(users.existsByEmail("ada@example.com")).isTrue();
    }

    @Test
    @DisplayName("响应中不出现任何密码字段")
    void neverReturnsPasswordFields() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ada@example.com", PASSWORD, "Ada")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("后台只保存哈希，不保存明文密码")
    void storesOnlyAHash() {
        authService.register("ada@example.com", PASSWORD, "Ada");

        User stored = users.findByEmail("ada@example.com").orElseThrow();
        assertThat(stored.passwordHash())
                .isNotEqualTo(PASSWORD)
                .startsWith("$2")
                .doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("同一密码两次注册得到不同哈希（加盐）")
    void hashesAreSalted() {
        authService.register("ada@example.com", PASSWORD, "Ada");
        authService.register("grace@example.com", PASSWORD, "Grace");

        String first = users.findByEmail("ada@example.com").orElseThrow().passwordHash();
        String second = users.findByEmail("grace@example.com").orElseThrow().passwordHash();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("重复邮箱返回 409 与可理解的提示")
    void rejectsDuplicateEmail() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ada@example.com", PASSWORD, "Ada")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ada@example.com", "Another-Pass-1", "另一个 Ada")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("该邮箱已注册，请直接登录"));
    }

    @Test
    @DisplayName("邮箱大小写与首尾空格视为同一个账号")
    void treatsEmailCaseInsensitively() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ada@Example.com", PASSWORD, "Ada")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("ada@example.com"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  ADA@example.COM  ", PASSWORD, "Ada")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("弱密码返回 400 且不创建账号")
    void rejectsWeakPassword() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ada@example.com", "short", "Ada")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("密码需为 8 至 64 位"));

        assertThat(users.existsByEmail("ada@example.com")).isFalse();
    }

    @Test
    @DisplayName("超长多字节密码不被截断，直接拒绝")
    void rejectsPasswordBeyondHashLimit() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ada@example.com", "安".repeat(25), "Ada")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码包含较多多字节字符，请缩短后重试"));

        assertThat(users.existsByEmail("ada@example.com")).isFalse();
    }

    @Test
    @DisplayName("邮箱格式错误返回 400")
    void rejectsMalformedEmail() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-an-email", PASSWORD, "Ada")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请填写有效的邮箱地址"));
    }

    @Test
    @DisplayName("空称呼返回 400")
    void rejectsBlankDisplayName() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ada@example.com", PASSWORD, "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请填写称呼"));
    }
}
