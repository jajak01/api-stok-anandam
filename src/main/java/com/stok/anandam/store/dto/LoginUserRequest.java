package com.stok.anandam.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Kredensial login")
public class LoginUserRequest {
    @NotBlank(message = "Username wajib diisi")
    @Schema(description = "Username terdaftar", example = "reza", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "Password wajib diisi")
    @Schema(description = "Password user", example = "rahasia", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
