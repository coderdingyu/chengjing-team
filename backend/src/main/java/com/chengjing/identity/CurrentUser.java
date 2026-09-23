package com.chengjing.identity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Injects the authenticated {@link AuthUser} into a controller method.
 *
 * <p>Controllers take the caller's identity from here, never from a request parameter or body:
 * that is what makes "only your own data" structural rather than a check someone can forget to
 * write (docs/需求分析说明书.md §8).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface CurrentUser {
}
