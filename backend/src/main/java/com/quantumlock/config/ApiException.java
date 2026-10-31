package com.quantumlock.config;

import org.springframework.http.HttpStatus;

/** Every deliberate failure in the API. The handler turns it into a JSON error body. */
public class ApiException extends RuntimeException {
    public final HttpStatus status;
    public final String code;
    public final String detail;

    public ApiException(HttpStatus status, String code, String message, String detail) {
        super(message);
        this.status = status;
        this.code = code;
        this.detail = detail;
    }

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }
}
