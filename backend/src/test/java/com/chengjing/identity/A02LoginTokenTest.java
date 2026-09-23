package com.chengjing.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chengjing.app.ChengjingApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-A02 acceptance: the right password yields a token and the wrong one is refused; after signing
 * out, the old token can no longer read the owner's profile.
 *
 * <p>NFR-01 also asks for automated coverage of unauthorised access, so the refusals below are as
 * much the point as the successes.
 */
@SpringBootTest(classes = ChengjingApplication.class)
@AutoConfigureMockMvc
class A02LoginTokenTest {
    private static final String PASSWORD = "Chengjing-2026";

    @Autowired MockMvc mvc;
    @Autowired InMemoryUserStore users;
    @Autowired TokenRevocations revocations;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void reset() {
        users.clear();
        revocations.clear();
    }

    // --- helpers -----------------------------------------------------------------------------

    private String register(String email, String displayName) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"%s"}
                                """.formatted(email, PASSWORD, displayName)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(response).path("data").path("token").asText();
    }

    private String login(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(response).path("data").path("token").asText();
    }

    private org.springframework.test.web.servlet.ResultActions me(String token) throws Exception {
        var request = get("/api/v1/auth/me");
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request);
    }

    // --- signing in --------------------------------------------------------------------------

    @Test
    @DisplayName("正确密码拿到令牌与账号资料")
    void signsInWithCorrectPassword() throws Exception {
        register("ada@example.com", "Ada");

        String token = login("ada@example.com", PASSWORD);
        assertThat(token).isNotBlank();

        me(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ada@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("Ada"));
    }

    @Test
    @DisplayName("注册后直接拿到可用令牌")
    void registrationSignsTheNewAccountIn() throws Exception {
        String token = register("ada@example.com", "Ada");
        me(token).andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value("ada@example.com"));
    }

    @Test
    @DisplayName("错误密码被拒绝，且不签发令牌")
    void rejectsWrongPassword() throws Exception {
        register("ada@example.com", "Ada");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("邮箱或密码错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("未注册邮箱与错误密码返回同样的提示，不泄露账号是否存在")
    void doesNotRevealWhetherAnAccountExists() throws Exception {
        register("ada@example.com", "Ada");

        String unknown = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"Chengjing-2026"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String wrongPassword = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(unknown).isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("邮箱大小写不影响登录")
    void signsInRegardlessOfEmailCase() throws Exception {
        register("ada@example.com", "Ada");
        assertThat(login("ADA@Example.com", PASSWORD)).isNotBlank();
    }

    // --- protected access --------------------------------------------------------------------

    @Test
    @DisplayName("未带令牌访问本人资料被拒绝")
    void rejectsMissingToken() throws Exception {
        me(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期，请重新登录"));
    }

    @Test
    @DisplayName("伪造或篡改的令牌被拒绝")
    void rejectsMalformedAndTamperedTokens() throws Exception {
        me("not-a-jwt").andExpect(status().isUnauthorized());

        String token = register("ada@example.com", "Ada");
        String tampered = token.substring(0, token.length() - 3) + "xyz";
        me(tampered).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("令牌只解析出本人账号")
    void tokenResolvesToItsOwnAccount() throws Exception {
        String ada = register("ada@example.com", "Ada");
        String grace = register("grace@example.com", "Grace");

        me(ada).andExpect(jsonPath("$.data.email").value("ada@example.com"));
        me(grace).andExpect(jsonPath("$.data.email").value("grace@example.com"));
    }

    @Test
    @DisplayName("账号停用后其令牌立即失效")
    void rejectsTokenOfDisabledAccount() throws Exception {
        String token = register("ada@example.com", "Ada");
        me(token).andExpect(status().isOk());

        User stored = users.findByEmail("ada@example.com").orElseThrow();
        users.save(stored.withEnabled(false));

        me(token).andExpect(status().isUnauthorized());
    }

    // --- signing out -------------------------------------------------------------------------

    @Test
    @DisplayName("登出后原令牌不能再取本人资料")
    void logoutEndsTheSession() throws Exception {
        String token = register("ada@example.com", "Ada");
        me(token).andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        me(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登出只结束本次会话，其他设备仍可用")
    void logoutLeavesOtherSessionsSignedIn() throws Exception {
        register("ada@example.com", "Ada");
        String firstDevice = login("ada@example.com", PASSWORD);
        String secondDevice = login("ada@example.com", PASSWORD);

        mvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstDevice))
                .andExpect(status().isOk());

        me(firstDevice).andExpect(status().isUnauthorized());
        me(secondDevice).andExpect(status().isOk());
    }

    @Test
    @DisplayName("登出后仍可用密码重新登录")
    void canSignInAgainAfterLogout() throws Exception {
        String token = register("ada@example.com", "Ada");
        mvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        String fresh = login("ada@example.com", PASSWORD);
        assertThat(fresh).isNotEqualTo(token);
        me(fresh).andExpect(status().isOk());
    }

    @Test
    @DisplayName("未登录时登出被拒绝")
    void rejectsLogoutWithoutToken() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    }
}
