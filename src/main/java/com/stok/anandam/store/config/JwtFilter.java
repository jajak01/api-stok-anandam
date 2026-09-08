package com.stok.anandam.store.config;

import com.stok.anandam.store.service.CustomUserDetailsService;
import com.stok.anandam.store.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private com.stok.anandam.store.core.postgres.repository.UserRepository userRepository;

    /** Skip JWT untuk path publik agar tidak parse token / hit DB (optimal). */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path != null && (path.startsWith("/api/v1/auth/login") ||
                path.startsWith("/api/v1/auth/refresh") ||
                path.startsWith("/api/v1/biometric/challenge") ||
                path.startsWith("/api/v1/biometric/verify") ||
                path.startsWith("/api/v1/public/app/latest") ||
                path.startsWith("/api/v1/public/tracking/") ||
                path.startsWith("/api/v1/internal/") ||
                path.startsWith("/download/") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/actuator/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        String username = null;
        String jwt = null;

        // 1. Cek Header Authorization
        // Format harus: "Bearer eyJhbGciOiJIUzI1NiJ9..."
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7); // Potong kata "Bearer "
            try {
                username = jwtUtil.extractUsername(jwt); // Ambil username dari token
            } catch (ExpiredJwtException e) {
                // Token expired → kembalikan 401 agar Flutter bisa trigger refresh/logout
                log.debug("JWT expired: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Token expired\"}");
                return; // Hentikan filter chain, jangan lanjut ke controller
            } catch (Exception e) {
                log.debug("JWT parse error: {}", e.getMessage());
            }
        }

        // 2. Validasi Token & Set Authentication
        // Jika username ketemu DAN belum ada yang login di context saat ini
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Ambil data user dari database
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                // Cek apakah user aktif/enabled
                if (!userDetails.isEnabled()) {
                    log.debug("User is disabled: {}", username);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Akun Anda telah dinonaktifkan. Silakan hubungi admin.\"}");
                    return;
                }

                // Cek apakah token valid (signature benar & belum expired)
                if (jwtUtil.validateToken(jwt, userDetails)) {
                    // Validasi pencabutan sesi (session revocation)
                    com.stok.anandam.store.core.postgres.model.User userEntity = userRepository.findByUsername(username).orElse(null);
                    if (userEntity != null && userEntity.getSessionsRevokedAt() != null) {
                        java.util.Date issuedAt = jwtUtil.extractIssuedAt(jwt);
                        if (issuedAt != null) {
                            java.time.Instant issuedInstant = issuedAt.toInstant();
                            java.time.Instant revokedInstant = userEntity.getSessionsRevokedAt().atZone(java.time.ZoneId.systemDefault()).toInstant();
                            if (issuedInstant.isBefore(revokedInstant)) {
                                log.debug("Session revoked for user: {}", username);
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response.getWriter().write(
                                        "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Sesi Anda telah berakhir. Silakan login kembali.\"}");
                                return;
                            }
                        }
                    }

                    // Buat objek Authentication
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Masukkan user ke context Spring Security (User dianggap LOGIN)
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                log.debug("User not found / deleted: {}", username);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"User tidak ditemukan atau sesi telah berakhir\"}");
                return;
            }
        }

        // 3. Lanjut ke filter berikutnya (atau ke Controller)
        filterChain.doFilter(request, response);
    }
}