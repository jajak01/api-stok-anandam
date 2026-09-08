package com.stok.anandam.store.dto;

import com.stok.anandam.store.core.postgres.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRequest {
    @NotBlank(message = "Nama wajib diisi")
    private String nama;

    @NotBlank(message = "Username wajib diisi")
    private String username;

    @Size(min = 6, message = "Password minimal 6 karakter")
    private String password;

    @NotNull(message = "Role wajib dipilih")
    private Role role;

    private String noHp;

    private String employeeCode;

    private java.util.Set<String> empCodeAccess;

    private java.util.Set<String> permissions;
}