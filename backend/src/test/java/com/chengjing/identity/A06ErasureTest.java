package com.chengjing.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * FR-A06 acceptance: erasure happens only after the account holder confirms it themselves;
 * without that confirmation nothing is erased.
 *
 * <p>"Nothing is erased" is asserted the strong way — the account still signs in, still reads its
 * profile and still exports its data — because a partial erasure is the failure that would matter.
 */
@SpringBootTest(classes = ChengjingApplication.class)
@AutoConfigureMockMvc
class A06ErasureTest {
    private static final String PASSWORD = "Chengjing-2026";
    private static final String PHRASE = AccountService.CONFIRMATION_PHRASE;

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

    private ResultActions erase(String token, String password, String confirmation) throws Exception {
        var request = delete("/api/v1/account").contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request.content("""
                {"currentPassword":"%s","confirmation":"%s"}
                """.formatted(password, confirmation)));
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

    /** Everything a live account can still do — used to prove a refused erasure changed nothing. */
    private void assertAccountIsIntact(String token) throws Exception {
        me(token).andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value("ada@example.com"));
        mvc.perform(get("/api/v1/account").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Ada"));
        mvc.perform(get("/api/v1/account/export").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        loginAttempt("ada@example.com", PASSWORD).andExpect(status().isOk());
    }

    // --- the confirmation gate ---------------------------------------------------------------

    @Test
    @DisplayName("确认短语与密码都正确时才执行注销")
    void erasesAfterConfirmation() throws Exception {
        String token = register("ada@example.com", "Ada");

        erase(token, PASSWORD, PHRASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.erasedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.backupNote").isNotEmpty());
    }

    @Test
    @DisplayName("没有确认短语时不执行，账号完好（验收关键点）")
    void doesNotEraseWithoutConfirmation() throws Exception {
        String token = register("ada@example.com", "Ada");

        erase(token, PASSWORD, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入「" + PHRASE + "」以确认注销"));

        assertAccountIsIntact(token);
    }

    @Test
    @DisplayName("确认短语写错时不执行，账号完好")
    void doesNotEraseWithWrongConfirmation() throws Exception {
        String token = register("ada@example.com", "Ada");

        erase(token, PASSWORD, "注销账号")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入「" + PHRASE + "」以确认注销"));

        assertAccountIsIntact(token);
    }

    @Test
    @DisplayName("确认短语对但密码错时不执行，账号完好")
    void doesNotEraseWithWrongPassword() throws Exception {
        String token = register("ada@example.com", "Ada");

        erase(token, "not-my-password", PHRASE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前密码不正确"));

        assertAccountIsIntact(token);
    }

    @Test
    @DisplayName("未登录不能注销")
    void rejectsEraseWithoutSignIn() throws Exception {
        register("ada@example.com", "Ada");

        erase(null, PASSWORD, PHRASE).andExpect(status().isUnauthorized());

        loginAttempt("ada@example.com", PASSWORD).andExpect(status().isOk());
    }

    // --- what erasure leaves behind ----------------------------------------------------------

    @Test
    @DisplayName("注销后原令牌失效，密码也不能再登录")
    void erasureEndsAccess() throws Exception {
        String token = register("ada@example.com", "Ada");
        erase(token, PASSWORD, PHRASE).andExpect(status().isOk());

        me(token).andExpect(status().isUnauthorized());
        loginAttempt("ada@example.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("注销让所有设备上的令牌失效，不只是当前这台")
    void erasureEndsEveryDevice() throws Exception {
        register("ada@example.com", "Ada");
        String firstDevice = login("ada@example.com", PASSWORD);
        String secondDevice = login("ada@example.com", PASSWORD);

        erase(firstDevice, PASSWORD, PHRASE).andExpect(status().isOk());

        me(firstDevice).andExpect(status().isUnauthorized());
        me(secondDevice).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("注销后账号记录里不再保留邮箱、显示名与可用密码")
    void erasureRemovesIdentifyingFields() throws Exception {
        String token = register("ada@example.com", "Ada");
        User before = users.findByEmail("ada@example.com").orElseThrow();
        String accountId = before.id();
        String hashBefore = before.passwordHash();

        erase(token, PASSWORD, PHRASE).andExpect(status().isOk());

        // The address no longer resolves to anything.
        assertThat(users.findByEmail("ada@example.com")).isEmpty();

        // The tombstone survives under its id, with nothing identifying left on it.
        User tombstone = users.findById(accountId).orElseThrow();
        assertThat(tombstone.enabled()).isFalse();
        assertThat(tombstone.email()).startsWith("deleted-").endsWith("@invalid.local");
        assertThat(tombstone.displayName()).isEqualTo(User.ERASED_DISPLAY_NAME);
        assertThat(tombstone.passwordHash()).isNotEqualTo(hashBefore).startsWith("$2");
        assertThat(tombstone.authVersion()).isGreaterThan(before.authVersion());
    }

    @Test
    @DisplayName("注销后邮箱可被重新注册，得到的是全新空账号")
    void emailBecomesAvailableAgain() throws Exception {
        String token = register("ada@example.com", "Ada");
        erase(token, PASSWORD, PHRASE).andExpect(status().isOk());

        String freshToken = register("ada@example.com", "Ada 新账号");
        me(freshToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Ada 新账号"));
    }

    @Test
    @DisplayName("注销不影响其他账号")
    void leavesOtherAccountsAlone() throws Exception {
        String ada = register("ada@example.com", "Ada");
        String grace = register("grace@example.com", "Grace");

        erase(ada, PASSWORD, PHRASE).andExpect(status().isOk());

        me(grace).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("grace@example.com"));
        loginAttempt("grace@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("注销结果说明备份保留边界")
    void receiptExplainsBackupRetention() throws Exception {
        String token = register("ada@example.com", "Ada");

        String body = erase(token, PASSWORD, PHRASE)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String note = mapper.readTree(body).path("data").path("backupNote").asText();
        assertThat(note).contains("备份").contains("活跃存储");
    }
}
