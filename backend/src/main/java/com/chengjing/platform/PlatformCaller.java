package com.chengjing.platform;

import java.lang.reflect.RecordComponent;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Reads the account module's authenticated AuthUser; never trusts a client-supplied owner ID. */
@Component
public class PlatformCaller {
    public String id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null)
            throw new PlatformException(HttpStatus.UNAUTHORIZED, "请先登录后管理模型配置");
        Object principal = auth.getPrincipal();
        // A's AuthUser record is merged separately; keeping this bridge here avoids a duplicate identity API.
        if (!principal.getClass().getName().equals("com.chengjing.identity.AuthUser"))
            throw new PlatformException(HttpStatus.UNAUTHORIZED, "请先登录后管理模型配置");
        try {
            for (RecordComponent component : principal.getClass().getRecordComponents()) {
                if (component.getName().equals("id")) {
                    String id = (String) component.getAccessor().invoke(principal);
                    if (id != null && !id.isBlank()) return id;
                }
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) { }
        throw new PlatformException(HttpStatus.UNAUTHORIZED, "请先登录后管理模型配置");
    }
}
