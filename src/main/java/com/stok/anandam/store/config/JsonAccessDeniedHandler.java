package com.stok.anandam.store.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Menangani user yang sudah login tetapi tidak punya izin (403) pada level
 * security filter. Body mengikuti wrapper standar.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    public static final String CODE = "ACCESS_DENIED";
    public static final String MESSAGE = "Anda tidak memiliki izin akses fitur ini";

    private final JsonErrorWriter errorWriter;

    public JsonAccessDeniedHandler(JsonErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        errorWriter.write(request, response, HttpStatus.FORBIDDEN, CODE, MESSAGE);
    }
}
