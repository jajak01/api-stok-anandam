package com.stok.anandam.store.dto;

import com.stok.anandam.store.core.postgres.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private Long id;
    private String nama;
    private String username;
    private String employeeCode;
    private Role role;
    private java.util.Collection<String> authorities;
    private boolean active;
    private Boolean isOnline;
    private Integer deviceCount;
    private String noHp;
    private java.util.Set<String> permissions;
    private java.util.Set<String> empCodeAccess;
    private java.time.LocalDateTime sessionsRevokedAt;
}