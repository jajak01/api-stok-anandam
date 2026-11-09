package com.stok.anandam.store.controller;

import com.stok.anandam.store.annotation.LogActivity;
import com.stok.anandam.store.core.postgres.model.RefreshToken;
import com.stok.anandam.store.docs.AuthApiDocs;
import com.stok.anandam.store.dto.*;
import com.stok.anandam.store.service.AuthService;
import com.stok.anandam.store.service.RefreshTokenService;
import com.stok.anandam.store.service.UserService;
import com.stok.anandam.store.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, refresh token, heartbeat, session, dan profil user")
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
        @Operation(summary = "Login user", description = "Menukar username + password menjadi access token (JWT) dan refresh token. "
                        + "Username tidak dikenal dan password salah sama-sama menghasilkan 401 INVALID_CREDENTIALS "
                        + "untuk mencegah user enumeration.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Login berhasil", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "success", value = AuthApiDocs.LOGIN_SUCCESS_EXAMPLE))),
                        @ApiResponse(responseCode = "400", description = "Field wajib tidak lengkap / format JSON salah", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "validation", value = AuthApiDocs.VALIDATION_EXAMPLE))),
                        @ApiResponse(responseCode = "401", description = "Username atau password salah", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "invalid-credentials", value = AuthApiDocs.INVALID_CREDENTIALS_EXAMPLE))),
                        @ApiResponse(responseCode = "403", description = "Akun dinonaktifkan / terkunci", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "account-deactivated", value = AuthApiDocs.ACCOUNT_DEACTIVATED_EXAMPLE))),
                        @ApiResponse(responseCode = "429", description = "Terlalu banyak percobaan login (jika rate limiting aktif)", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "too-many-attempts", value = AuthApiDocs.TOO_MANY_ATTEMPTS_EXAMPLE))),
                        @ApiResponse(responseCode = "500", description = "Error tak terduga di server", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "internal-error", value = AuthApiDocs.INTERNAL_ERROR_EXAMPLE)))
        })
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
        @Operation(summary = "Refresh access token", description = "Menukar refresh token yang masih valid dengan access token baru.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Token berhasil diperbarui", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "success", value = AuthApiDocs.TOKEN_REFRESHED_EXAMPLE))),
                        @ApiResponse(responseCode = "400", description = "Refresh token tidak dikirim", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "validation", value = AuthApiDocs.REFRESH_VALIDATION_EXAMPLE))),
                        @ApiResponse(responseCode = "401", description = "Refresh token tidak valid / expired", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "invalid-refresh-token", value = AuthApiDocs.INVALID_REFRESH_TOKEN_EXAMPLE))),
                        @ApiResponse(responseCode = "500", description = "Error tak terduga di server", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "internal-error", value = AuthApiDocs.INTERNAL_ERROR_EXAMPLE)))
        })
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
        @SecurityRequirement(name = "bearerAuth")
        @Operation(summary = "Ambil user yang sedang login")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Data user berhasil diambil"),
                        @ApiResponse(responseCode = "401", description = "Token tidak ada / expired", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "token-expired", value = AuthApiDocs.TOKEN_EXPIRED_EXAMPLE)))
        })
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
        @SecurityRequirement(name = "bearerAuth")
        @Operation(summary = "Logout user", description = "Menghapus refresh token dan menandai user offline. "
                        + "Token yang sudah expired tetap menghasilkan 200 agar logout di client selalu bersih.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Logout berhasil", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "success", value = AuthApiDocs.LOGOUT_SUCCESS_EXAMPLE))),
                        @ApiResponse(responseCode = "401", description = "Token tidak valid", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "token-expired", value = AuthApiDocs.TOKEN_EXPIRED_EXAMPLE)))
        })
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
        @SecurityRequirement(name = "bearerAuth")
        @Operation(summary = "Heartbeat status online", description = "Client mengirim sinyal ini secara berkala (mis. tiap 25 detik). "
                        + "Backend memperbarui lastActivity dan menandai user online.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Heartbeat diterima", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "success", value = AuthApiDocs.HEARTBEAT_SUCCESS_EXAMPLE))),
                        @ApiResponse(responseCode = "401", description = "Token tidak ada / expired", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "token-expired", value = AuthApiDocs.TOKEN_EXPIRED_EXAMPLE))),
                        @ApiResponse(responseCode = "403", description = "Akun dinonaktifkan", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "account-deactivated", value = AuthApiDocs.ACCOUNT_DEACTIVATED_EXAMPLE)))
        })
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
        @SecurityRequirement(name = "bearerAuth")
        @Operation(summary = "Ganti password user yang sedang login")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Password berhasil diubah"),
                        @ApiResponse(responseCode = "400", description = "Validasi gagal", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "validation", value = AuthApiDocs.VALIDATION_EXAMPLE))),
                        @ApiResponse(responseCode = "401", description = "Token tidak ada / expired", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "token-expired", value = AuthApiDocs.TOKEN_EXPIRED_EXAMPLE)))
        })
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
        @SecurityRequirement(name = "bearerAuth")
        @Operation(summary = "Ubah nomor HP user yang sedang login")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Nomor HP berhasil diubah"),
                        @ApiResponse(responseCode = "400", description = "Validasi gagal", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "validation", value = AuthApiDocs.VALIDATION_EXAMPLE))),
                        @ApiResponse(responseCode = "401", description = "Token tidak ada / expired", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "token-expired", value = AuthApiDocs.TOKEN_EXPIRED_EXAMPLE)))
        })
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
