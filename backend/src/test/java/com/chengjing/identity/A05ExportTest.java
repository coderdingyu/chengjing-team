package com.chengjing.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * FR-A05 acceptance: you can export your own records; nobody else can read them.
 *
 * <p>Scope note: this round exports what the identity module owns. The preparation, interview,
 * assessment and model records belong to modules that have not merged yet, and the export says so
 * rather than looking complete.
 *
 * <p>docs/需求分析说明书.md §8 requires that an export contains neither authentication tokens nor
 * model keys, so the credential assertions below are part of the acceptance, not a nicety.
 */
@SpringBootTest(classes = ChengjingApplication.class)
@AutoConfigureMockMvc
class A05ExportTest {
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

    private ResultActions export(String token) throws Exception {
        var request = get("/api/v1/account/export");
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private String exportBody(String token) throws Exception {
        return export(token)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // --- what the export contains ------------------------------------------------------------

    @Test
    @DisplayName("本人可以导出自己的账号资料")
    void exportsOwnAccount() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");

        export(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.formatVersion").value(1))
                .andExpect(jsonPath("$.data.exportedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.account.id").isNotEmpty())
                .andExpect(jsonPath("$.data.account.email").value("ada@example.com"))
                .andExpect(jsonPath("$.data.account.displayName").value("Ada"))
                .andExpect(jsonPath("$.data.account.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("导出里不含密码、令牌或密钥")
    void exportCarriesNoCredentials() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");
        String body = exportBody(token);

        assertThat(body)
                .doesNotContain("passwordHash")
                .doesNotContain("password")
                .doesNotContain("\"token\"")
                .doesNotContain("secret")
                .doesNotContain("Bearer");

        // The stored hash must not appear anywhere in the payload either.
        String storedHash = users.findByEmail("ada@example.com").orElseThrow().passwordHash();
        assertThat(body).doesNotContain(storedHash);
    }

    @Test
    @DisplayName("导出说明哪些数据本轮尚未包含，不假装完整")
    void exportNamesWhatIsMissing() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");

        export(token)
                .andExpect(jsonPath("$.data.notIncludedYet").isArray())
                .andExpect(jsonPath("$.data.notIncludedYet").isNotEmpty());
    }

    @Test
    @DisplayName("改名后导出反映最新资料")
    void exportReflectsTheLatestProfile() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Ada Lovelace"}
                                """))
                .andExpect(status().isOk());

        export(token).andExpect(jsonPath("$.data.account.displayName").value("Ada Lovelace"));
    }

    // --- who can read it ---------------------------------------------------------------------

    @Test
    @DisplayName("未登录不能导出")
    void rejectsExportWithoutSignIn() throws Exception {
        tokenFor("ada@example.com", "Ada");

        export(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或登录已过期，请重新登录"));
    }

    @Test
    @DisplayName("每个人导出到的都是自己，拿不到别人的数据")
    void eachAccountExportsOnlyItsOwnData() throws Exception {
        String ada = tokenFor("ada@example.com", "Ada");
        String grace = tokenFor("grace@example.com", "Grace");

        String adaExport = exportBody(ada);
        String graceExport = exportBody(grace);

        assertThat(adaExport)
                .contains("ada@example.com")
                .doesNotContain("grace@example.com");
        assertThat(graceExport)
                .contains("grace@example.com")
                .doesNotContain("ada@example.com");
    }

    @Test
    @DisplayName("伪造令牌不能导出")
    void rejectsTamperedToken() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");
        String tampered = token.substring(0, token.length() - 3) + "xyz";

        export(tampered).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("账号停用后不能导出")
    void rejectsDisabledAccount() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");
        export(token).andExpect(status().isOk());

        users.save(users.findByEmail("ada@example.com").orElseThrow().withEnabled(false));

        export(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登出后不能导出")
    void rejectsExportAfterSignOut() throws Exception {
        String token = tokenFor("ada@example.com", "Ada");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        export(token).andExpect(status().isUnauthorized());
    }
}
