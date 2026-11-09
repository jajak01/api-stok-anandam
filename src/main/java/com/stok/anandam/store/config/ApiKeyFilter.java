package com.stok.anandam.store.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter keamanan berbasis API Key untuk endpoint internal.
 * Hanya request dengan header X-Internal-Key yang valid yang bisa mengakses /api/v1/internal/**.
 */
@Component
@Order(1)
public class ApiKeyFilter extends OncePerRequestFilter {

    @Value("${app.internal-api-key:CHANGE_ME_TO_A_RANDOM_SECRET}")
    private String validApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Hanya filter untuk path /api/v1/internal/**
        return !request.getServletPath().startsWith("/api/v1/internal");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-Internal-Key");

        if (apiKey == null || !apiKey.equals(validApiKey)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":403,\"message\":\"Invalid or missing API Key\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
