package com.stok.anandam.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Permintaan refresh access token")
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token wajib diisi")
    @Schema(description = "Refresh token yang diterima saat login", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
