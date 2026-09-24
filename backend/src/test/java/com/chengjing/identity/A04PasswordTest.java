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
import org.springframework.test.web.servlet.ResultActions;

/**
 * FR-A04 acceptance: the old password is required before a new one is set, and once it is set the
 * tokens issued earlier stop working.
 *
 * <p>"Stops working" is checked the way it matters in practice — by replaying a token that was
 * valid a moment ago — rather than by inspecting the stored auth version.
 */
@SpringBootTest(classes = ChengjingApplication.class)
@AutoConfigureMockMvc
class A04PasswordTest {
    private static final String PASSWORD = "Chengjing-2026";
    private static final String NEW_PASSWORD = "Lovelace-1852-Notes";

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

    private ResultActions changePassword(String token, String current, String next) throws Exception {
        var request = post("/api/v1/account/password").contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request.content("""
                {"currentPassword":"%s","newPassword":"%s"}
                """.formatted(current, next)));
    }

    private ResultActions me(String token) throws Exception {
        var request = get("/api/v1/auth/me");
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private ResultActions loginAttempt(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    // --- the old password gates the change ---------------------------------------------------

    @Test
    @DisplayName("旧密码正确才能设置新密码")
    void changesPasswordWithTheCorrectCurrentPassword() throws Exception {
        String token = register("ada@example.com", "Ada");

        changePassword(token, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("旧密码错误返回 400，密码不变且原令牌仍可用")
    void rejectsWrongCurrentPassword() throws Exception {
        String token = register("ada@example.com", "Ada");

        changePassword(token, "not-my-password", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前密码不正确"));

        me(token).andExpect(status().isOk());
        loginAttempt("ada@example.com", PASSWORD).andExpect(status().isOk());
        loginAttempt("ada@example.com", NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未登录不能改密码")
    void rejectsChangeWithoutSignIn() throws Exception {
        register("ada@example.com", "Ada");

        changePassword(null, PASSWORD, NEW_PASSWORD).andExpect(status().isUnauthorized());

        loginAttempt("ada@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("空字段返回 400")
    void rejectsBlankFields() throws Exception {
        String token = register("ada@example.com", "Ada");

        changePassword(token, "", NEW_PASSWORD).andExpect(status().isBadRequest());
        changePassword(token, PASSWORD, "").andExpect(status().isBadRequest());
        loginAttempt("ada@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("新密码过弱返回 400，密码不变")
    void rejectsWeakNewPassword() throws Exception {
        String token = register("ada@example.com", "Ada");

        changePassword(token, PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码需为 8 至 64 位"));

        loginAttempt("ada@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("新密码与当前密码相同返回 400")
    void rejectsReusingTheCurrentPassword() throws Exception {
        String token = register("ada@example.com", "Ada");

        changePassword(token, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("新密码不能与当前密码相同"));

        me(token).andExpect(status().isOk());
    }

    // --- sessions after the change -----------------------------------------------------------

    @Test
    @DisplayName("改密码后旧令牌失效（验收关键点）")
    void invalidatesTokensIssuedBeforeTheChange() throws Exception {
        String token = register("ada@example.com", "Ada");
        me(token).andExpect(status().isOk());

        changePassword(token, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        me(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("改密码让所有设备上的令牌失效，不只是当前这台")
    void invalidatesEveryDevice() throws Exception {
        register("ada@example.com", "Ada");
        String firstDevice = login("ada@example.com", PASSWORD);
        String secondDevice = login("ada@example.com", PASSWORD);

        changePassword(firstDevice, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        me(firstDevice).andExpect(status().isUnauthorized());
        me(secondDevice).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("改密码后新密码可登录，旧密码不可")
    void newPasswordReplacesTheOldOne() throws Exception {
        String token = register("ada@example.com", "Ada");
        changePassword(token, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        loginAttempt("ada@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("邮箱或密码错误"));

        String fresh = login("ada@example.com", NEW_PASSWORD);
        me(fresh).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ada@example.com"));
    }

    @Test
    @DisplayName("改密码不影响其他账号")
    void leavesOtherAccountsAlone() throws Exception {
        String ada = register("ada@example.com", "Ada");
        String grace = register("grace@example.com", "Grace");

        changePassword(ada, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        me(grace).andExpect(status().isOk());
        loginAttempt("grace@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("新密码用新的盐重新哈希，不与旧哈希相同")
    void rehashesWithAFreshSalt() throws Exception {
        String token = register("ada@example.com", "Ada");
        String before = users.findByEmail("ada@example.com").orElseThrow().passwordHash();

        changePassword(token, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        String after = users.findByEmail("ada@example.com").orElseThrow().passwordHash();
        assertThat(after).isNotEqualTo(before).startsWith("$2");
    }

    // --- signing out everywhere --------------------------------------------------------------

    @Test
    @DisplayName("退出所有设备后所有令牌失效，但密码仍可登录")
    void logoutEverywhereEndsAllSessions() throws Exception {
        register("ada@example.com", "Ada");
        String firstDevice = login("ada@example.com", PASSWORD);
        String secondDevice = login("ada@example.com", PASSWORD);

        mvc.perform(post("/api/v1/account/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstDevice))
                .andExpect(status().isOk());

        me(firstDevice).andExpect(status().isUnauthorized());
        me(secondDevice).andExpect(status().isUnauthorized());

        // The password itself was never changed, so signing back in still works.
        loginAttempt("ada@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("退出所有设备不影响其他账号")
    void logoutEverywhereLeavesOtherAccountsAlone() throws Exception {
        String ada = register("ada@example.com", "Ada");
        String grace = register("grace@example.com", "Grace");

        mvc.perform(post("/api/v1/account/logout-all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ada));

        me(grace).andExpect(status().isOk());
    }

    @Test
    @DisplayName("未登录不能退出所有设备")
    void rejectsLogoutEverywhereWithoutSignIn() throws Exception {
        mvc.perform(post("/api/v1/account/logout-all")).andExpect(status().isUnauthorized());
    }
}
