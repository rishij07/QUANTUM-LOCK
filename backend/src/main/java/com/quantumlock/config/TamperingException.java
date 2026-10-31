package com.quantumlock.config;

import org.springframework.http.HttpStatus;

public class TamperingException extends ApiException {
    public TamperingException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "TAMPERING_DETECTED",
                "TAMPERING DETECTED \u2014 AUTHENTICATION FAILED",
                "The AES-256-GCM authentication tag did not verify. No plaintext was released.");
    }
}
