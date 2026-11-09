package com.stok.anandam.store.exception;

import com.stok.anandam.store.dto.ApiErrorResponse;
import com.stok.anandam.store.dto.WebResponse;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        private static final String MSG_BAD_CREDENTIALS = "Username atau password salah";
        private static final String MSG_ACCOUNT_DEACTIVATED = "Akun Anda telah dinonaktifkan. Silakan hubungi admin.";
        private static final String MSG_INTERNAL_ERROR = "Terjadi kesalahan internal pada server. Silakan hubungi admin.";

        // 1. Handle Data Tidak Ditemukan (404)
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleResourceNotFound(ResourceNotFoundException ex,
                        HttpServletRequest request) {
                return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not Found", ex.getMessage(), request, null);
        }

        // 2. Handle Error Database (Contoh: Duplicate Entry, Kolom Kepanjangan) (409)
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleDataIntegrity(DataIntegrityViolationException ex,
                        HttpServletRequest request) {
                String message = "Terjadi konflik data database. Cek constraint atau panjang karakter.";
                if (ex.getRootCause() != null) {
                        message = ex.getRootCause().getMessage();
                }
                return buildResponse(HttpStatus.CONFLICT, "DATA_CONFLICT", "Database Conflict", message, request, null);
        }

        // 3. Handle Tipe Data Salah di URL (Contoh: ?page=abc padahal harus int) (400)
        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                        HttpServletRequest request) {
                String message = String.format("Parameter '%s' harus berupa tipe '%s'",
                                ex.getName(), ex.getRequiredType().getSimpleName());
                return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Bad Request", message, request, null);
        }

        // 4. Handle Parameter Kurang (400)
        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleMissingParams(
                        MissingServletRequestParameterException ex, HttpServletRequest request) {
                String message = "Parameter wajib '" + ex.getParameterName() + "' tidak ditemukan.";
                return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Missing Parameter", message, request,
                                null);
        }

        // 5. Handle Argument tidak valid dari service (400)
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleIllegalArgument(IllegalArgumentException ex,
                        HttpServletRequest request) {
                return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Bad Request", ex.getMessage(), request,
                                null);
        }

        // 5b. Konflik status / aturan bisnis (409) — mis. edit nota yang terkunci SN
        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleIllegalState(IllegalStateException ex,
                        HttpServletRequest request) {
                return buildResponse(HttpStatus.CONFLICT, "CONFLICT", "Conflict", ex.getMessage(), request, null);
        }

        // 5a. Format tanggal tidak valid (startDate/endDate) (400)
        @ExceptionHandler(DateTimeParseException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleDateTimeParse(DateTimeParseException ex,
                        HttpServletRequest request) {
                String message = "Format tanggal tidak valid. Gunakan dd-MM-yyyy (contoh: 15-01-2024).";
                return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Bad Request", message, request, null);
        }

        // 5c. Handle JSON Syntax / Enum Typo (400)
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleMessageNotReadable(HttpMessageNotReadableException ex,
                        HttpServletRequest request) {
                String message = "Format JSON tidak valid atau ada kesalahan penulisan (Typo).";
                if (ex.getMessage() != null && ex.getMessage().contains("Enum")) {
                        message = "Kesalahan penulisan status/enum. Silakan cek kembali pilihan status yang diizinkan.";
                }
                return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Bad Request", message, request, null);
        }

        // 5d. Handle Invalid Refresh Token (401)
        @ExceptionHandler(InvalidRefreshTokenException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleInvalidRefreshToken(InvalidRefreshTokenException ex,
                        HttpServletRequest request) {
                return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Unauthorized",
                                ex.getMessage(), request, null);
        }

        // 5e. Kredensial salah — password salah maupun username tidak dikenal (401).
        // Pesan sengaja disamakan untuk mencegah user enumeration.
        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleBadCredentials(BadCredentialsException ex,
                        HttpServletRequest request) {
                log.warn("Authentication failed: path={}, ip={}, reason=BAD_CREDENTIALS",
                                request.getRequestURI(), getClientIp(request));
                return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Unauthorized",
                                MSG_BAD_CREDENTIALS, request, null);
        }

        // 5f. Akun dinonaktifkan / terkunci (403)
        @ExceptionHandler({ DisabledException.class, LockedException.class })
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleAccountStatus(AuthenticationException ex,
                        HttpServletRequest request) {
                log.warn("Authentication failed: path={}, ip={}, reason=ACCOUNT_DEACTIVATED",
                                request.getRequestURI(), getClientIp(request));
                return buildResponse(HttpStatus.FORBIDDEN, "ACCOUNT_DEACTIVATED", "Forbidden",
                                MSG_ACCOUNT_DEACTIVATED, request, null);
        }

        // 5g. Fallback untuk AuthenticationException lain (mis. token/credential tidak valid).
        // Kegagalan infrastruktur saat memuat user (DB down) tetap dipetakan ke 500.
        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleAuthenticationException(AuthenticationException ex,
                        HttpServletRequest request) {
                if (ex instanceof InternalAuthenticationServiceException) {
                        log.error("Unexpected authentication service error at {}: {}",
                                        request.getRequestURI(), ex.getMessage(), ex);
                        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                                        "Internal Server Error", MSG_INTERNAL_ERROR, request, null);
                }
                log.warn("Authentication failed: path={}, ip={}, reason={}",
                                request.getRequestURI(), getClientIp(request), ex.getClass().getSimpleName());
                return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Unauthorized",
                                MSG_BAD_CREDENTIALS, request, null);
        }

        // 6. Handle Validasi DTO (Bean Validation) (400)
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleValidation(MethodArgumentNotValidException ex,
                        HttpServletRequest request) {
                Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                                .collect(Collectors.toMap(
                                                err -> err.getField(),
                                                err -> err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid",
                                                (a, b) -> a));
                String message = fieldErrors.isEmpty() ? "Validasi gagal" : fieldErrors.values().iterator().next();
                return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation Failed", message, request,
                                fieldErrors);
        }

        // 6a. Handle ResponseStatusException (manual status code + message)
        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleResponseStatus(ResponseStatusException ex,
                        HttpServletRequest request) {
                int statusCode = ex.getStatusCode().value();
                String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
                return buildResponse(HttpStatus.valueOf(statusCode), codeForStatus(statusCode),
                                ex.getStatusCode().toString(), message, request, null);
        }

        // 7. Handle Akses Ditolak (403)
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleAccessDenied(AccessDeniedException ex,
                        HttpServletRequest request) {
                String message = "Akses ditolak: Anda tidak berhak melihat dokumen ini.";
                return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Forbidden", message, request, null);
        }

        // 8. Handle Biometric Authentication Failed (401)
        @ExceptionHandler(BiometricAuthenticationException.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleBiometricException(
                        BiometricAuthenticationException ex, HttpServletRequest request) {
                return buildResponse(HttpStatus.UNAUTHORIZED, "BIOMETRIC_AUTH_FAILED",
                                "Biometric Authentication Failed", ex.getMessage(), request, null);
        }

        // 9. Handle Semua Error Lain yang Tidak Terduga (500)
        @ExceptionHandler(Exception.class)
        public ResponseEntity<WebResponse<ApiErrorResponse>> handleGlobalException(Exception ex,
                        HttpServletRequest request) {
                log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
                return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                                "Internal Server Error", MSG_INTERNAL_ERROR, request, null);
        }

        // --- Helpers ---

        private ResponseEntity<WebResponse<ApiErrorResponse>> buildResponse(HttpStatus status, String code,
                        String error, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
                ApiErrorResponse body = new ApiErrorResponse(
                                status.value(),
                                code,
                                error,
                                message,
                                request.getRequestURI(),
                                fieldErrors);
                return ResponseEntity.status(status).body(WebResponse.<ApiErrorResponse>builder()
                                .status(status.value())
                                .message(message)
                                .data(body)
                                .paging(null)
                                .build());
        }

        private String codeForStatus(int status) {
                return switch (status) {
                        case 400 -> "VALIDATION_ERROR";
                        case 401 -> "UNAUTHORIZED";
                        case 403 -> "ACCESS_DENIED";
                        case 404 -> "NOT_FOUND";
                        case 409 -> "CONFLICT";
                        case 429 -> "TOO_MANY_ATTEMPTS";
                        default -> status >= 500 ? "INTERNAL_ERROR" : null;
                };
        }

        private String getClientIp(HttpServletRequest request) {
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                        return xff.split(",")[0].trim();
                }
                return request.getRemoteAddr();
        }

        // --- Custom Exception Classes ---

        public static class BiometricAuthenticationException extends RuntimeException {
                public BiometricAuthenticationException(String message) {
                        super(message);
                }
        }
}
