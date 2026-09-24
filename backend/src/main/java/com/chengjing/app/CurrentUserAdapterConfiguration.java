package com.chengjing.app;

import com.chengjing.assessment.scoring.CurrentUserPort;
import com.chengjing.identity.AuthUser;
import com.chengjing.shared.ApiException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Wires member A's authenticated identity to the ports other modules declared for it.
 *
 * <p>This lives in the composition root rather than in {@code identity} or {@code assessment} so
 * that neither module has to depend on the other: A keeps providing the current user, D keeps
 * consuming one, and only the application wiring knows about both (docs/模块契约.md §3).
 *
 * <p><strong>Why {@code @Primary}.</strong> {@code AssessmentPortConfiguration} registers a
 * fallback {@code CurrentUserPort} guarded by {@code @ConditionalOnMissingBean}. That condition is
 * only reliable inside auto-configuration; between two ordinary {@code @Configuration} classes the
 * processing order is undefined, so the fallback may or may not be registered first. If both beans
 * end up registered, {@code EvidenceScoringController}'s by-type injection would fail with
 * {@code NoUniqueBeanDefinitionException}. Marking this one primary makes the outcome the same
 * either way — the authenticated adapter always wins, and the fallback is simply never selected.
 */
@Configuration
public class CurrentUserAdapterConfiguration {

    /**
     * Resolves the signed-in account from the bearer token that {@code JwtAuthFilter} validated.
     *
     * <p>An unauthenticated call is answered as "not signed in" (401) rather than as "service
     * unavailable": the scoring endpoint needs a caller, and the caller simply has not signed in.
     */
    @Bean
    @Primary
    public CurrentUserPort authenticatedCurrentUserPort() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !(authentication.getPrincipal() instanceof AuthUser caller)) {
                throw ApiException.unauthorized("未登录或登录已过期，请重新登录");
            }
            return caller.id();
        };
    }
}
