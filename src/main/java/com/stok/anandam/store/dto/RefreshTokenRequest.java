package com.stok.anandam.store.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token wajib diisi")
    private String refreshToken;
}