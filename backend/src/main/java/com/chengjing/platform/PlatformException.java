package com.chengjing.platform;

import org.springframework.http.HttpStatus;

public class PlatformException extends RuntimeException {
    private final HttpStatus status;

    public PlatformException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
