package com.stok.anandam.store.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

@Data
public class ApiErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private Map<String, String> fieldErrors;

    public ApiErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, null);
    }

    public ApiErrorResponse(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyMap();
    }
}