package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.DefaultRolePermissions;
import com.stok.anandam.store.core.postgres.model.Role;
import com.stok.anandam.store.core.postgres.model.User;
import com.stok.anandam.store.core.postgres.repository.RefreshTokenRepository;
import com.stok.anandam.store.core.postgres.repository.UserRepository;
import com.stok.anandam.store.dto.ChangePasswordRequest;
import com.stok.anandam.store.dto.UserRequest;
import com.stok.anandam.store.dto.UserResponse;
import com.stok.anandam.store.dto.UpdatePhoneRequest;
import com.stok.anandam.store.exception.ResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private UserSessionService userSessionService;

    /**
     * List user dengan filter opsional: search (nama/username), role. Jika halaman
     * melebihi total, kembalikan halaman 0.
     */
    public Page<UserResponse> getAllUsers(int page, int size, String search, String roleStr) {
        Pageable pageable = PageRequest.of(page, size);
        Role role = null;
        if (roleStr != null && !roleStr.isBlank()) {
            try {
                role = Role.valueOf(roleStr.toUpperCase().trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        Page<User> usersPage = userRepository.findByFilters(search, role, pageable);
        if (usersPage.getTotalPages() > 0 && page >= usersPage.getTotalPages()) {
            pageable = PageRequest.of(0, size);
            usersPage = userRepository.findByFilters(search, role, pageable);
        }
        List<UserResponse> userResponses = usersPage.getContent().stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(userResponses, usersPage.getPageable(), usersPage.getTotalElements());
    }

    public UserResponse createUser(UserRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password wajib diisi saat membuat user");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new DataIntegrityViolationException("Username '" + request.getUsername() + "' sudah terpakai");
        }
        User user = new User();
        user.setNama(request.getNama());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setNoHp(request.getNoHp());
        user.setEmployeeCode(request.getEmployeeCode());
        if (request.getEmpCodeAccess() != null) {
            user.setEmpCodeAccess(new java.util.HashSet<>(request.getEmpCodeAccess()));
        }
        if (request.getPermissions() != null && !request.getPermissions().isEmpty()) {
            user.setPermissions(new java.util.HashSet<>(request.getPermissions()));
        } else {
            user.setPermissions(new java.util.HashSet<>(DefaultRolePermissions.getDefaultPermissions(request.getRole())));
        }
        User savedUser = userRepository.save(user);
        return toUserResponse(savedUser);
    }

    @org.springframework.transaction.annotation.Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User dengan ID " + id + " tidak ditemukan"));

        if (!user.getUsername().equals(request.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new DataIntegrityViolationException(
                        "Username '" + request.getUsername() + "' sudah digunakan user lain");
            }
        }

        user.setNama(request.getNama());
        user.setUsername(request.getUsername());
        user.setRole(request.getRole());
        user.setNoHp(request.getNoHp());
        if (request.getEmployeeCode() != null) {
            user.setEmployeeCode(request.getEmployeeCode());
        }
        if (request.getEmpCodeAccess() != null) {
            user.setEmpCodeAccess(new java.util.HashSet<>(request.getEmpCodeAccess()));
        }
        if (request.getPermissions() != null) {
            user.setPermissions(new java.util.HashSet<>(request.getPermissions()));
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        userRepository.save(user);
        return toUserResponse(user);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User dengan ID " + id + " tidak ditemukan"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = auth != null ? auth.getName() : "";

        if (user.getUsername().equals(currentUsername)) {
            throw new DataIntegrityViolationException("Anda tidak dapat menghapus akun sendiri yang sedang aktif!");
        }

        // 1. Invalidate session & revoke refresh tokens immediately across all devices
        user.setActive(false);
        user.setSessionsRevokedAt(LocalDateTime.now());
        user.setIsOnline(false);
        user.setDeviceCount(0);

        refreshTokenRepository.findByUser(user).ifPresent(refreshToken -> {
            refreshTokenRepository.delete(refreshToken);
        });
        refreshTokenRepository.deleteByUser(user);

        if (userSessionService != null) {
            userSessionService.removeUserSession(user.getUsername());
            userSessionService.setUserOffline(user.getUsername());
        }

        // 2. Try hard-delete first; if foreign key integrity prevents deletion, maintain soft-deleted state
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (Exception e) {
            // Keep user soft-deleted / inactive so historical transactions remain intact
            user.setActive(false);
            userRepository.save(user);
        }
    }

    /**
     * Ambil data user yang sedang login (current authenticated user)
     */
    public UserResponse getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ResourceNotFoundException("User tidak ditemukan atau belum login");
        }

        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(
                        () -> new ResourceNotFoundException("User dengan username " + username + " tidak ditemukan"));

        return toUserResponse(user);
    }

    @org.springframework.transaction.annotation.Transactional
    public void changePassword(ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Password lama salah");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @org.springframework.transaction.annotation.Transactional
    public UserResponse toggleUserStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User dengan ID " + id + " tidak ditemukan"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (user.getUsername().equals(auth.getName())) {
            throw new DataIntegrityViolationException(
                    "Anda tidak dapat menonaktifkan akun sendiri yang sedang digunakan!");
        }

        boolean nextActive = !Boolean.TRUE.equals(user.getActive());
        user.setActive(nextActive);
        if (!nextActive) {
            user.setSessionsRevokedAt(LocalDateTime.now());
            user.setIsOnline(false);
            user.setDeviceCount(0);
            refreshTokenRepository.deleteByUser(user);
            if (userSessionService != null) {
                userSessionService.removeUserSession(user.getUsername());
                userSessionService.setUserOffline(user.getUsername());
            }
        }
        userRepository.save(user);
        return toUserResponse(user);
    }

    @org.springframework.transaction.annotation.Transactional
    public UserResponse clearUserSessions(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User dengan ID " + id + " tidak ditemukan"));

        // 1. Reset device count and online status
        user.setDeviceCount(0);
        user.setIsOnline(false);
        user.setSessionsRevokedAt(LocalDateTime.now());
        userRepository.save(user);

        // 2. Delete all refresh tokens for this user
        refreshTokenRepository.deleteByUser(user);
        if (userSessionService != null) {
            userSessionService.removeUserSession(user.getUsername());
            userSessionService.setUserOffline(user.getUsername());
        }

        // 3. Log activity
        activityLogService.log(user.getUsername(), "CLEAR_SESSIONS", "Seluruh sesi user dibersihkan oleh admin");

        return toUserResponse(user);
    }

    @org.springframework.transaction.annotation.Transactional
    public UserResponse updateUserEmpScope(Long id, java.util.Set<String> empScope) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User dengan ID " + id + " tidak ditemukan"));
        user.setEmpCodeAccess(empScope != null ? new java.util.HashSet<>(empScope) : new java.util.HashSet<>());
        userRepository.save(user);
        return toUserResponse(user);
    }

    public java.util.Set<String> getUserEmpScope(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User dengan ID " + id + " tidak ditemukan"));
        return user.getEmpCodeAccess() != null ? new java.util.HashSet<>(user.getEmpCodeAccess()) : new java.util.HashSet<>();
    }

    @org.springframework.transaction.annotation.Transactional
    public void processHeartbeat(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastActivity(LocalDateTime.now());
            user.setIsOnline(true);
            userRepository.save(user);
            if (userSessionService != null) {
                userSessionService.updateUserAction(user.getUsername(), user.getNama(), "active");
            }
        });
    }

    @org.springframework.transaction.annotation.Transactional
    public UserResponse updatePhone(UpdatePhoneRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));

        user.setNoHp(request.getNoHp());
        userRepository.save(user);

        // Log activity
        activityLogService.log(username, "UPDATE_PROFILE_PHONE", "User mengubah nomor HP menjadi: " + request.getNoHp());

        return toUserResponse(user);
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setNama(user.getNama());
        response.setUsername(user.getUsername());
        response.setEmployeeCode(user.getEmployeeCode());
        response.setRole(user.getRole());

        java.util.Set<String> perms = user.getPermissions();
        if (perms == null || perms.isEmpty()) {
            perms = DefaultRolePermissions.getDefaultPermissions(user.getRole());
        }
        response.setPermissions(new java.util.HashSet<>(perms));

        java.util.List<String> authorities = new java.util.ArrayList<>();
        authorities.add("ROLE_" + user.getRole().name());
        authorities.addAll(perms);
        response.setAuthorities(authorities);

        response.setActive(Boolean.TRUE.equals(user.getActive()));

        // Server-side calculated online status based on recent heartbeat (within 60 seconds)
        boolean isOnline = Boolean.TRUE.equals(user.getIsOnline()) &&
                user.getLastActivity() != null &&
                user.getLastActivity().isAfter(LocalDateTime.now().minusSeconds(60));
        response.setIsOnline(isOnline);
        response.setDeviceCount(user.getDeviceCount());
        response.setNoHp(user.getNoHp());
        response.setEmpCodeAccess(user.getEmpCodeAccess() != null ? new java.util.HashSet<>(user.getEmpCodeAccess()) : new java.util.HashSet<>());
        response.setSessionsRevokedAt(user.getSessionsRevokedAt());
        return response;
    }
}