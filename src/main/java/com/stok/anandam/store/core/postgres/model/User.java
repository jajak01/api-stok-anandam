package com.stok.anandam.store.core.postgres.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nama;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder.Default
    @Column(columnDefinition = "boolean default true")
    private Boolean active = true;

    private String noHp;

    @Column(name = "employee_code")
    private String employeeCode;

    @Builder.Default
    @Column(columnDefinition = "boolean default false")
    private Boolean isOnline = false;

    @Builder.Default
    @Column(columnDefinition = "integer default 0")
    private Integer deviceCount = 0;

    private LocalDateTime lastActivity;

    private LocalDateTime lastLogin;

    @Column(name = "sessions_revoked_at")
    private LocalDateTime sessionsRevokedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission")
    @Builder.Default
    private java.util.Set<String> permissions = new java.util.HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_emp_code_access", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "emp_code")
    @Builder.Default
    private java.util.Set<String> empCodeAccess = new java.util.HashSet<>();
}