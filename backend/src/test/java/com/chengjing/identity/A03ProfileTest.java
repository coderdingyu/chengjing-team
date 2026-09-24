package com.chengjing.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * FR-A03 acceptance: after signing in, read and change your own display name; another account's
 * profile is out of reach.
 *
 * <p>The "another account" cases matter most. Because {@code /account} takes no account id, the
 * only way to attempt it is to call the endpoint with someone else's token — which must change
 * nothing of the other person's.
 */
@SpringBootTest(classes = ChengjingApplication.class)
@AutoConfigureMockMvc
class A03ProfileTest {
    private static final String PASSWORD = "Chengjing-2026";

    @Autowired MockMvc mvc;
    @Autowired InMemoryUserStore users;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void reset() {
        users.clear();
    }

    // --- helpers -----------------------------------------------------------------------------

    private String tokenFor(String email, String displayName) throws Exception {
        String response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"%s"}
                                """.formatted(email, PASSWORD, displayName)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(response).path("data").path("token").asText();
    }

    private ResultActions getAccount(String token) throws Exception {
        var request = get("/api/v1/account");
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private ResultActions rename(String token, String displayName) throws Exception {
        var request = put("/api/v1/account").contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request.content("""
                {"displayName":"%s"}
                """.formatted(displayName)));
    }

    // --- reading -----------------------------------------------------------------------------

    @Test
    @DisplayName("登录后读取本人资料")
    void readsOwnProfile() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");

        getAccount(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("ada@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("Ada"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("资料中不含密码字段")
    void profileCarriesNoCredentials() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");

        getAccount(token)
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("未登录读取资料被拒绝")
    void rejectsReadingWithoutSignIn() throws Exception {
        getAccount(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或登录已过期，请重新登录"));
    }

    // --- changing ----------------------------------------------------------------------------

    @Test
    @DisplayName("登录后修改自己的显示名")
    void renamesOwnProfile() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");

        rename(token, "Ada Lovelace")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.data.email").value("ada@example.com"));

        getAccount(token).andExpect(jsonPath("$.data.displayName").value("Ada Lovelace"));
    }

    @Test
    @DisplayName("改名后 /auth/me 立即反映新名字")
    void renameIsVisibleToTheAuthEndpoint() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");
        rename(token, "Ada Lovelace").andExpect(status().isOk());

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Ada Lovelace"));
    }

    @Test
    @DisplayName("称呼首尾空格被去除")
    void trimsDisplayName() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");

        rename(token, "  Ada Lovelace  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Ada Lovelace"));
    }

    @Test
    @DisplayName("空称呼返回 400")
    void rejectsBlankDisplayName() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");

        rename(token, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请填写称呼"));

        getAccount(token).andExpect(jsonPath("$.data.displayName").value("Ada"));
    }

    @Test
    @DisplayName("超长称呼返回 400")
    void rejectsOverlongDisplayName() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");

        rename(token, "a".repeat(81))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("称呼最多 80 个字"));

        getAccount(token).andExpect(jsonPath("$.data.displayName").value("Ada"));
    }

    @Test
    @DisplayName("未登录修改资料被拒绝")
    void rejectsRenamingWithoutSignIn() throws Exception {
        tokenFor("ada@example.com", "Ada");

        rename(null, "Intruder").andExpect(status().isUnauthorized());

        assertThat(users.findByEmail("ada@example.com").orElseThrow().displayName())
                .isEqualTo("Ada");
    }

    // --- the other person's profile ----------------------------------------------------------

    @Test
    @DisplayName("用别人的令牌只能改到自己，改不动对方")
    void cannotChangeAnotherAccount() throws Exception {
        tokenFor("ada@example.com", "Ada");
        String graceToken = tokenFor("grace@example.com", "Grace");

        // Grace acts with her own token. Nothing in the request can name Ada.
        rename(graceToken, "Grace Hopper")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("grace@example.com"));

        assertThat(users.findByEmail("ada@example.com").orElseThrow().displayName())
                .isEqualTo("Ada");
        assertThat(users.findByEmail("grace@example.com").orElseThrow().displayName())
                .isEqualTo("Grace Hopper");
    }

    @Test
    @DisplayName("每个人读到的都是自己")
    void eachCallerSeesTheirOwnAccount() throws Exception {
        String ada = tokenFor("ada@example.com", "Ada");
        String grace = tokenFor("grace@example.com", "Grace");

        getAccount(ada).andExpect(jsonPath("$.data.email").value("ada@example.com"));
        getAccount(grace).andExpect(jsonPath("$.data.email").value("grace@example.com"));
    }

    @Test
    @DisplayName("账号停用后不能再读改资料")
    void rejectsDisabledAccount() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");
        getAccount(token).andExpect(status().isOk());

        users.save(users.findByEmail("ada@example.com").orElseThrow().withEnabled(false));

        getAccount(token).andExpect(status().isUnauthorized());
    }
}
