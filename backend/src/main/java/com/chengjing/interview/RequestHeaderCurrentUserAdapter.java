package com.chengjing.interview;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Owner: member C (interview module).
 *
 * Temporary stand-in for member A's authenticated principal while A02 is still an open PR.
 * The browser keeps a stable per-device id, so a refreshed page can still reopen its own room,
 * and a room can never be reached with somebody else's id.
 *
 * This adapter deliberately does not pretend to be authentication: it is only an owner key for the
 * skeleton, and it is replaced (not extended) when A02 provides the real principal.
 */
@Component
public class RequestHeaderCurrentUserAdapter implements CurrentUserPort {

    public static final String HEADER = "X-Chengjing-User";

    private static final String SHAPE = "[A-Za-z0-9_-]{6,64}";

    private final HttpServletRequest request;

    public RequestHeaderCurrentUserAdapter(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public String requireUserId() {
        String value = request.getHeader(HEADER);
        if (value == null || !value.trim().matches(SHAPE)) {
            throw InterviewException.unauthorized("缺少有效的用户标识，请重新进入面试");
        }
        return value.trim();
    }
}
