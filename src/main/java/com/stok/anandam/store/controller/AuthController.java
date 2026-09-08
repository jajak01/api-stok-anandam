package com.stok.anandam.store.controller;

import com.stok.anandam.store.annotation.LogActivity;
import com.stok.anandam.store.core.postgres.model.RefreshToken;
import com.stok.anandam.store.dto.*;
import com.stok.anandam.store.service.AuthService;
import com.stok.anandam.store.service.RefreshTokenService;
import com.stok.anandam.store.service.UserService;
import com.stok.anandam.store.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

        @Autowired
        private AuthService authService;

        @Autowired
        private RefreshTokenService refreshTokenService;

        @Autowired
        private JwtUtil jwtUtil;

        @Autowired
        private UserService userService;

        // 1. LOGIN (Dapat 2 Token)
        @PostMapping("/login")
        @LogActivity("USER LOGIN KE SYSTEM")
        public ResponseEntity<WebResponse<TokenResponse>> login(@Valid @RequestBody LoginUserRequest request) {
                // Autentikasi User (Cek password dll di AuthService)
                authService.authenticate(request);

                // Generate Access Token (Pendek)
                String accessToken = jwtUtil.generateToken(request.getUsername());

                // Generate Refresh Token (Panjang & Masuk DB)
                RefreshToken refreshToken = refreshTokenService.createRefreshToken(request.getUsername());

                TokenResponse tokenResponse = TokenResponse.builder()
                                .accessToken(accessToken)
                                .refreshToken(refreshToken.getToken())
                                .type("Bearer")
                                .build();

                WebResponse<TokenResponse> response = WebResponse.<TokenResponse>builder()
                                .status(HttpStatus.OK.value())
                                .message("Login Berhasil")
                                .data(tokenResponse)
                                .paging(null)
                                .build();

                return ResponseEntity.ok(response);
        }

        // 2. REFRESH TOKEN (Tukar Token Lama -> Baru)
        @PostMapping("/refresh")
        public ResponseEntity<WebResponse<TokenResponse>> refreshToken(
                        @Valid @RequestBody RefreshTokenRequest request) {
                String requestRefreshToken = request.getRefreshToken();

                return refreshTokenService.findByToken(requestRefreshToken)
                                .map(refreshTokenService::verifyExpiration) // Cek expired
                                .map(RefreshToken::getUser) // Ambil user-nya
                                .map(user -> {
                                        // Bikin Access Token Baru
                                        String accessToken = jwtUtil.generateToken(user.getUsername());

                                        TokenResponse tokenResponse = TokenResponse.builder()
                                                        .accessToken(accessToken)
                                                        .refreshToken(requestRefreshToken) // Balikin refresh token yg
                                                                                           // sama (atau bisa diputar)
                                                        .type("Bearer")
                                                        .build();

                                        return ResponseEntity.ok(WebResponse.<TokenResponse>builder()
                                                        .status(200).message("Token Refreshed").data(tokenResponse)
                                                        .paging(null).build());
                                })
                                .orElseThrow(() -> new com.stok.anandam.store.exception.InvalidRefreshTokenException(
                                                "Refresh token tidak ditemukan atau tidak valid"));
        }

        // 3. GET CURRENT USER (/me)
        /**
         * Ambil data user yang sedang login
         * Endpoint ini memerlukan JWT token di header Authorization: Bearer <token>
         */
        @GetMapping("/me")
        public ResponseEntity<WebResponse<UserResponse>> getCurrentUser() {
                UserResponse userResponse = userService.getCurrentUser();

                WebResponse<UserResponse> response = WebResponse.<UserResponse>builder()
                                .status(HttpStatus.OK.value())
                                .message("Success fetch current user")
                                .data(userResponse)
                                .paging(null)
                                .build();

                return ResponseEntity.ok(response);
        }

        @PostMapping("/logout")
        @LogActivity("USER LOGOUT DARI SYSTEM")
        public ResponseEntity<WebResponse<String>> logout() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                        refreshTokenService.deleteByUsername(auth.getName());
                        authService.logout();
                }

                return ResponseEntity.ok(WebResponse.<String>builder()
                                .status(200)
                                .message("Logout Berhasil")
                                .data("OK")
                                .paging(null)
                                .build());
        }

        @PostMapping("/heartbeat")
        public ResponseEntity<WebResponse<String>> heartbeat() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                        userService.processHeartbeat(auth.getName());
                }

                return ResponseEntity.ok(WebResponse.<String>builder()
                                .status(200)
                                .message("Heartbeat OK")
                                .data("OK")
                                .paging(null)
                                .build());
        }

        @PostMapping("/change-password")
        @LogActivity("USER GANTI PASSWORD SENDIRI")
        public ResponseEntity<WebResponse<String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
                userService.changePassword(request);

                return ResponseEntity.ok(WebResponse.<String>builder()
                                .status(HttpStatus.OK.value())
                                .message("Password Berhasil Diubah")
                                .data("OK")
                                .paging(null)
                                .build());
        }

        @PutMapping("/profile/phone")
        @LogActivity("USER UPDATE PROFILE PHONE")
        public ResponseEntity<WebResponse<UserResponse>> updatePhone(@Valid @RequestBody UpdatePhoneRequest request) {
                UserResponse userResponse = userService.updatePhone(request);

                WebResponse<UserResponse> response = WebResponse.<UserResponse>builder()
                                .status(HttpStatus.OK.value())
                                .message("Nomor HP berhasil diubah")
                                .data(userResponse)
                                .paging(null)
                                .build();

                return ResponseEntity.ok(response);
        }
}
