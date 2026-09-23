package com.chengjing.identity;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Authentication rules for the API (FR-A02).
 *
 * <p><strong>Opt-in protection.</strong> {@code anyRequest().permitAll()} is the default on
 * purpose. Adding {@code spring-boot-starter-security} makes this chain run for every request in
 * the application, so a deny-by-default rule would silently start rejecting the endpoints members
 * B, C, D and E are still writing. Instead, a module protects its own paths by adding them to the
 * list below — one line each, visible in review.
 *
 * <p>This is a shared file by nature, so changes to the list are worth calling out in a PR
 * (docs/协作上传指南.md §3).
 *
 * <p>No CORS configuration: the Vite dev server proxies {@code /api} to the backend, so browser
 * calls are same-origin. A deployment that serves the two from different origins needs to add one.
 */
@Configuration
@EnableWebSecurity
public class IdentitySecurityConfig {
    private static final String UNAUTHENTICATED = "未登录或登录已过期，请重新登录";
    private static final String FORBIDDEN = "无权访问";

    private final JwtAuthFilter jwtAuthFilter;

    public IdentitySecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain identityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless bearer tokens: there is no session cookie for CSRF to protect.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers("/api/v1/system/health").permitAll()
                        // A's own endpoints.
                        .requestMatchers("/api/v1/account/**").authenticated()
                        .requestMatchers("/api/v1/auth/logout", "/api/v1/auth/me").authenticated()
                        // B/C/D/E add their protected paths here. Everything else stays open so
                        // this change does not alter behaviour for modules that have not opted in.
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, FORBIDDEN)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Answers in the shared {success,data,message} envelope rather than Spring's default page. */
    private static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"" + message + "\"}");
    }
}
