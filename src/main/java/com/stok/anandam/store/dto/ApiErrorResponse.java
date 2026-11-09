package com.stok.anandam.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

@Data
@Schema(description = "Detail error yang dibungkus di dalam field 'data' pada WebResponse")
public class ApiErrorResponse {
    @Schema(description = "Waktu kejadian error", example = "2026-11-10T05:44:06.214093659")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP status code", example = "401")
    private int status;

    @Schema(description = "Kode error yang stabil untuk dibaca client", example = "INVALID_CREDENTIALS")
    private String code;

    @Schema(description = "Alasan HTTP status", example = "Unauthorized")
    private String error;

    @Schema(description = "Pesan yang aman ditampilkan ke user", example = "Username atau password salah")
    private String message;

    @Schema(description = "Path endpoint yang menghasilkan error", example = "/api/v1/auth/login")
    private String path;

    @Schema(description = "Error per field (khusus validasi)")
    private Map<String, String> fieldErrors;

    public ApiErrorResponse(int status, String error, String message, String path) {
        this(status, null, error, message, path, null);
    }

    public ApiErrorResponse(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        this(status, null, error, message, path, fieldErrors);
    }

    public ApiErrorResponse(int status, String code, String error, String message, String path) {
        this(status, code, error, message, path, null);
    }

    public ApiErrorResponse(int status, String code, String error, String message, String path,
            Map<String, String> fieldErrors) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.code = code;
        this.error = error;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyMap();
    }
}
