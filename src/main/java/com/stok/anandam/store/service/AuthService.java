package com.stok.anandam.store.service;


import com.stok.anandam.store.core.postgres.repository.UserRepository;
import com.stok.anandam.store.dto.LoginUserRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogService activityLogService;

    // Method ini hanya bertugas memverifikasi password
    public void authenticate(LoginUserRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Update status and device count
            userRepository.findByUsername(request.getUsername()).ifPresent(user -> {
                user.setLastLogin(LocalDateTime.now());
                user.setLastActivity(LocalDateTime.now());
                user.setIsOnline(true);
                // Karena RefreshTokenService membatasi 1 user = 1 token, maka count selalu 1 saat login
                user.setDeviceCount(1);
                userRepository.save(user);
                
                // Log activity
                activityLogService.log(request.getUsername(), "USER_LOGIN", "Berhasil login ke aplikasi");
            });
        } catch (DisabledException e) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Akun Anda telah dinonaktifkan. Silakan hubungi admin.");}}

    @Autowired
    private RefreshTokenService refreshTokenService;

    public void logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return;
        
        String username = auth.getName();
        userRepository.findByUsername(username).ifPresent(user -> {
            // Hapus token refresh dari database agar benar-benar logout
            refreshTokenService.deleteByUsername(username);
            
            // Reset status online dan device count
            user.setDeviceCount(0);
            user.setIsOnline(false);
            userRepository.save(user);
            
            // Log activity
            activityLogService.log(username, "USER_LOGOUT", "Berhasil logout dari aplikasi");
        });
    }
}