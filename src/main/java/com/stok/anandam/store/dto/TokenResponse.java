package com.stok.anandam.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data; // <--- INI OBATNYA
import lombok.NoArgsConstructor;

@Data // <--- Pastikan ini ada
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Pasangan access token dan refresh token")
public class TokenResponse {
    @Schema(description = "JWT access token (short-lived)", example = "<jwt>")
    private String accessToken;

    @Schema(description = "Refresh token (long-lived, tersimpan di DB)", example = "<uuid>")
    private String refreshToken;

    @Builder.Default
    @Schema(description = "Tipe token", example = "Bearer")
    private String type = "Bearer";
}
