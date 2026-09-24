package com.chengjing.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chengjing.assessment.scoring.CurrentUserPort;
import com.chengjing.identity.AuthUser;
import com.chengjing.shared.ApiException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the handshake between A's identity module and the port D declared for it.
 *
 * <p>{@code AssessmentPortConfiguration} registers a fallback {@code CurrentUserPort} that answers
 * "尚未配置". These tests pin down that the authenticated adapter is the one actually used — if the
 * fallback ever won, every scoring call would report a configuration problem instead of scoring.
 */
@SpringBootTest(classes = ChengjingApplication.class)
@AutoConfigureMockMvc
class CurrentUserAdapterConfigurationTest {

    @Autowired CurrentUserPort currentUserPort;
    @Autowired MockMvc mvc;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthUser(userId, "ada@example.com", "Ada"), "token", List.of()));
    }

    @Test
    @DisplayName("已登录时端口返回当前用户 ID，而不是「尚未配置」")
    void resolvesTheSignedInUserId() {
        signIn("account-1");

        assertThat(currentUserPort.requireUserId()).isEqualTo("account-1");
    }

    @Test
    @DisplayName("未登录时端口以「未登录」拒绝")
    void refusesWhenNobodyIsSignedIn() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> currentUserPort.requireUserId())
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    @DisplayName("D 的评分接口未登录时返回 401，而不是 503 未配置")
    void scoringEndpointAsksForSignIn() throws Exception {
        mvc.perform(post("/api/v1/assessments/answers/answer-1/score"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期，请重新登录"));
    }
}
