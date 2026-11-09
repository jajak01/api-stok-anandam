package com.stok.anandam.store.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Menangani request tanpa token / token tidak valid yang ditolak oleh
 * security filter. Body yang ditulis mengikuti wrapper standar.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String CODE = "TOKEN_EXPIRED";
    public static final String MESSAGE = "Sesi habis atau token tidak valid";

    private final JsonErrorWriter errorWriter;

    public JsonAuthenticationEntryPoint(JsonErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, CODE, MESSAGE);
    }
}
