package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.DefaultRolePermissions;
import com.stok.anandam.store.core.postgres.model.Role;
import com.stok.anandam.store.core.postgres.model.User;
import com.stok.anandam.store.core.postgres.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Cari user di database
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User tidak ditemukan: " + username));

        // 2. Terjemahkan Role & Permissions ke Authority Spring Security
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (user.getRole() == Role.MANAGER || user.getRole() == Role.ADMIN) {
            // MANAGER & ADMIN = akses penuh, semua role & semua permission
            Arrays.stream(Role.values())
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                    .forEach(authorities::add);
            DefaultRolePermissions.getAllPermissions().stream()
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
            Set<String> perms = user.getPermissions();
            if (perms == null || perms.isEmpty()) {
                perms = DefaultRolePermissions.getDefaultPermissions(user.getRole());
            }
            if (perms != null) {
                perms.stream()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }
        }

        // 3. Kembalikan object User milik Spring Security (bukan User entity kita)
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getActive()), // enabled
                true, // accountNonExpired
                true, // credentialsNonExpired
                true, // accountNonLocked
                authorities
        );
    }
}