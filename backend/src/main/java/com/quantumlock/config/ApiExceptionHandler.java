package com.quantumlock.config;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        return body(e.status.value(), e.code, e.getMessage(), e.detail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> any(Exception e) {
        if (e instanceof MaxUploadSizeExceededException) {
            return body(413, "TOO_LARGE", "File exceeds the 25 MB demo limit", null);
        }
        if (e instanceof HttpMessageNotReadableException || e instanceof MethodArgumentTypeMismatchException
                || e instanceof MultipartException) {
            return body(400, "BAD_REQUEST", "Malformed request", null);
        }
        if (e instanceof ErrorResponse er) {
            return body(er.getStatusCode().value(), "REQUEST_REJECTED", "Request rejected", null);
        }
        // Fail closed: never leak internals, never return partial results.
        log.error("Unhandled error - failing closed", e);
        return body(500, "FAILED_CLOSED", "Request failed closed", "An internal error occurred. No data was released.");
    }

    private static ResponseEntity<Map<String, Object>> body(int status, String code, String message, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("code", code);
        m.put("message", message);
        m.put("detail", detail);
        m.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(m);
    }
}
