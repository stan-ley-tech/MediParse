package com.mediparse.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> fieldErrors
) {
    public ApiError(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path, List.of());
    }

    public ApiError(int status, String error, String message, String path, List<String> fieldErrors) {
        this(Instant.now(), status, error, message, path, fieldErrors);
    }
}
