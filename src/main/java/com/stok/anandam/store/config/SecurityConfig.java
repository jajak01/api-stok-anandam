package com.stok.anandam.store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final UserDetailsService userDetailsService;
        private final JwtFilter jwtFilter;

        public SecurityConfig(UserDetailsService userDetailsService, JwtFilter jwtFilter) {
                this.userDetailsService = userDetailsService;
                this.jwtFilter = jwtFilter;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public DaoAuthenticationProvider authenticationProvider() {
                DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
                authProvider.setPasswordEncoder(passwordEncoder());
                return authProvider;
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
                return authConfig.getAuthenticationManager();
        }

        /**
         * Helper: Memeriksa apakah IP client ada di whitelist (localhost / internal kantor).
         * Mendukung X-Forwarded-For untuk akses dibalik reverse proxy (Nginx).
         */
        private boolean isIpWhitelisted(HttpServletRequest request) {
                String ip = getClientIp(request);
                if (ip == null) return false;
                return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || ip.startsWith("192.168.1.");
        }

        /**
         * Helper: Mendapatkan IP asli client dengan dukungan X-Forwarded-For.
         */
        private String getClientIp(HttpServletRequest request) {
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                        return xff.split(",")[0].trim();
                }
                return request.getRemoteAddr();
        }

        /** Actuator: health & info (Tanpa Auth), lainnya butuh ADMIN */
        @Bean
        @Order(1)
        public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .securityMatcher("/actuator/**")
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                                                .anyRequest().hasRole("ADMIN"))
                                .csrf(csrf -> csrf.disable());
                return http.build();
        }

        /** API (JWT Based) dengan penanganan 401 & 403 yang benar */
        @Bean
        @Order(2)
        public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .securityMatcher("/api/**")
                                .cors(Customizer.withDefaults())
                                .csrf(csrf -> csrf.disable())

                                // --- PENANGANAN ERROR AUTH/AUTHZ ---
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                                        response.setContentType("application/json");
                                                        response.getWriter().write(
                                                                        "{\"code\": \"TOKEN_EXPIRED\", \"message\": \"Sesi habis atau token tidak valid\"}");
                                                })
                                                .accessDeniedHandler((request, response, accessDeniedException) -> {
                                                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                                        response.setContentType("application/json");
                                                        response.getWriter().write(
                                                                        "{\"code\": \"ACCESS_DENIED\", \"message\": \"Anda tidak memiliki izin akses fitur ini\"}");
                                                }))

                                .authorizeHttpRequests(auth -> auth
                                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                                        .requestMatchers("/api/v1/biometric/challenge", "/api/v1/biometric/verify").permitAll()
                                        .requestMatchers("/api/v1/public/app/latest").permitAll()
                                        .requestMatchers("/api/v1/public/tracking/**").permitAll()
                                        .requestMatchers("/api/v1/internal/**").permitAll() // Secured by ApiKeyFilter
                                        .requestMatchers("/download/**").permitAll()
                                        .requestMatchers("/ws/**", "/ws-connect/**").permitAll()
                                        
                                        // REVISI: Izinkan semua user yang login untuk akses log sinkronisasi & profil sendiri
                                        .requestMatchers("/api/v1/activity-logs/last-sync").authenticated()
                                        .requestMatchers("/api/v1/users/me", "/api/v1/users/profile", "/api/v1/kodepos").authenticated() 

                                        // PRINTER: Wajib login (membawa token) agar tidak bisa print spam dari luar
                                        .requestMatchers("/api/v1/printer/**").authenticated()

                                        // STOK & SN
                                        // Mengubah persen hargaButtom: hanya ADMIN/MANAGER (dicek SEBELUM matcher stocks umum di bawah)
                                        .requestMatchers(HttpMethod.PUT, "/api/v1/stocks/harga-buttom-percent", "/api/v1/stock/harga-buttom-percent").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER")
                                        .requestMatchers("/api/v1/stock", "/api/v1/stock/**", "/api/v1/stocks", "/api/v1/stocks/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "STOK", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_MARKETING", "ROLE_MARKETING_TOKO", "ROLE_MARKETING_PROJECT", "ROLE_MARKETING_DISTRIBUSI", "ROLE_MARKETING_ONLINE", "ROLE_GUDANG", "ROLE_DELIVERY", "ROLE_TEKNISI", "ROLE_NOTA")
                                        .requestMatchers("/api/sn", "/api/sn/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "ITEM_SN", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_MARKETING", "ROLE_MARKETING_TOKO", "ROLE_MARKETING_PROJECT", "ROLE_MARKETING_DISTRIBUSI", "ROLE_MARKETING_ONLINE", "ROLE_GUDANG", "ROLE_TEKNISI", "ROLE_DELIVERY", "ROLE_NOTA")

                                        // PENJADWALAN
                                        .requestMatchers(HttpMethod.GET, "/api/v1/penjadwalan", "/api/v1/penjadwalan/**").authenticated()
                                        .requestMatchers(HttpMethod.PUT, "/api/v1/penjadwalan/*/reassign", "/api/v1/penjadwalan/*/reassign/").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "PENJADWALAN", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_MARKETING", "ROLE_MARKETING_TOKO", "ROLE_MARKETING_PROJECT", "ROLE_MARKETING_DISTRIBUSI", "ROLE_MARKETING_ONLINE", "ROLE_GUDANG")
                                        .requestMatchers(HttpMethod.POST, "/api/v1/penjadwalan", "/api/v1/penjadwalan/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "PENJADWALAN", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_MARKETING", "ROLE_MARKETING_TOKO", "ROLE_MARKETING_PROJECT", "ROLE_MARKETING_DISTRIBUSI", "ROLE_MARKETING_ONLINE", "ROLE_GUDANG", "ROLE_TEKNISI", "ROLE_DELIVERY")
                                        .requestMatchers(HttpMethod.PUT, "/api/v1/penjadwalan", "/api/v1/penjadwalan/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "PENJADWALAN", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_MARKETING", "ROLE_MARKETING_TOKO", "ROLE_MARKETING_PROJECT", "ROLE_MARKETING_DISTRIBUSI", "ROLE_MARKETING_ONLINE", "ROLE_GUDANG", "ROLE_TEKNISI", "ROLE_DELIVERY")

                                        // MEMOS (Strict RBAC)
                                        .requestMatchers(HttpMethod.POST, "/api/v1/memos/*/penjadwalan", "/api/v1/memos/*/konfirmasi-kirim").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "MEMO", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_MARKETING", "ROLE_MARKETING_TOKO", "ROLE_MARKETING_PROJECT", "ROLE_MARKETING_DISTRIBUSI", "ROLE_MARKETING_ONLINE", "ROLE_GUDANG", "ROLE_TEKNISI", "ROLE_DELIVERY")
                                        .requestMatchers(HttpMethod.POST, "/api/v1/memos", "/api/v1/memos/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "MEMO", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_MARKETING", "ROLE_MARKETING_TOKO", "ROLE_MARKETING_PROJECT", "ROLE_MARKETING_DISTRIBUSI", "ROLE_MARKETING_ONLINE")
                                        .requestMatchers(HttpMethod.PUT, "/api/v1/memos/*/gudang-finish", "/api/v1/memos/*/invoice-finish", "/api/v1/memos/*/technician-finish", "/api/v1/memos/*/delivery-finish", "/api/v1/memos/*/pickup-route", "/api/v1/memos/*/pickup-final", "/api/v1/memos/*/delivery-route", "/api/v1/memos/*/complete").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "MEMO", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_GUDANG", "ROLE_TEKNISI", "ROLE_DELIVERY", "ROLE_MARKETING", "ROLE_MARKETING_TOKO", "ROLE_MARKETING_PROJECT", "ROLE_MARKETING_DISTRIBUSI", "ROLE_MARKETING_ONLINE", "ROLE_NOTA")
                                        .requestMatchers(HttpMethod.PUT, "/api/v1/memos", "/api/v1/memos/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "MEMO", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_MARKETING", "ROLE_MARKETING_TOKO", "ROLE_MARKETING_PROJECT", "ROLE_MARKETING_DISTRIBUSI", "ROLE_MARKETING_ONLINE", "ROLE_GUDANG", "ROLE_DELIVERY", "ROLE_TEKNISI", "ROLE_NOTA")
                                        .requestMatchers(HttpMethod.GET, "/api/v1/memos", "/api/v1/memos/**").authenticated()
                                        
                                        // REQUEST DELIVERY (New System)
                                        .requestMatchers(HttpMethod.GET, "/api/v1/request-delivery", "/api/v1/request-delivery/", "/api/v1/request-delivery/**").authenticated()
                                        .requestMatchers(HttpMethod.POST, "/api/v1/request-delivery", "/api/v1/request-delivery/", "/api/v1/request-delivery/**").authenticated()

                                        // DELIVERY ASSIGNMENT & SCAN
                                        .requestMatchers("/api/v1/delivery", "/api/v1/delivery/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "PENGIRIMAN", "ROLE_DELIVERY", "ROLE_GUDANG")

                                        // SERVIS
                                        .requestMatchers("/api/v1/transaksi-servis/**", "/api/v1/pelanggan-servis/**", "/api/v1/laporan-servis/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "SERVIS", "ROLE_SPV_TEKNISI", "ROLE_TEKNISI")

                                        // PEMBELIAN & PENJUALAN
                                        // Export Excel PEMBELIAN/PENJUALAN/PIUTANG/HUTANG: hanya ADMIN & MANAGER
                                        .requestMatchers("/api/v1/sales/export", "/api/v1/sale/export", "/api/v1/sales/export/*",
                                                        "/api/v1/purchases/export", "/api/v1/purchase/export", "/api/v1/purchases/export/*",
                                                        "/api/v1/piutang/export", "/api/v1/piutangs/export", "/api/v1/piutang/export/*",
                                                        "/api/v1/hutang/export", "/api/v1/hutangs/export", "/api/v1/hutang/export/*")
                                .hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER")
                                        .requestMatchers("/api/v1/purchases/**", "/api/v1/purchase/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "PEMBELIAN", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_TEKNISI")
                                        .requestMatchers("/api/v1/sales/**", "/api/v1/sale/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "PENJUALAN", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_TEKNISI")

                                        // HUTANG (pembelian by status): gate sama seperti Pembelian (permission PEMBELIAN)
                                        .requestMatchers("/api/v1/hutang", "/api/v1/hutangs", "/api/v1/hutang/**", "/api/v1/hutangs/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "PEMBELIAN", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_TEKNISI")
                                        // PIUTANG (penjualan by status): permission PIUTANG (ADMIN/MANAGER punya semua permission)
                                        .requestMatchers("/api/v1/piutang", "/api/v1/piutangs", "/api/v1/piutang/**", "/api/v1/piutangs/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "PIUTANG")

                                        // DATA WAREHOUSE & CANVAS
                                        .requestMatchers("/api/v1/old-data/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "DATA_WAREHOUSE", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_TEKNISI")
                                        .requestMatchers("/api/v1/canvasings/**", "/api/v1/canvasing/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "CANVAS", "ROLE_SPV_MARKETING", "ROLE_MARKETING", "ROLE_MARKETING_TOKO", "ROLE_MARKETING_PROJECT", "ROLE_MARKETING_DISTRIBUSI", "ROLE_MARKETING_ONLINE")
                                        .requestMatchers("/api/v1/data-canvasing/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "DATA_CANVAS", "ROLE_SPV_MARKETING")

                                        // MASTER DATA LAINNYA
                                        .requestMatchers("/api/v1/tkdns/**", "/api/v1/tkdn/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "TKDN", "ROLE_SPV_MARKETING", "ROLE_MARKETING", "ROLE_MARKETING_TOKO", "ROLE_MARKETING_PROJECT", "ROLE_MARKETING_DISTRIBUSI", "ROLE_MARKETING_ONLINE")
                                        .requestMatchers("/api/v1/ijin-import/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "IJIN_IMPORT")
                                        .requestMatchers("/api/v1/shbj/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "SHBJ")
                                        .requestMatchers("/api/v1/announcements/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "ANNOUNCEMENT")

                                        // Endpoint Admin & Spv (Data sensitif)
                                        .requestMatchers(HttpMethod.GET, "/api/v1/users", "/api/v1/users/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "USERS", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI", "ROLE_GUDANG", "ROLE_MARKETING", "ROLE_MARKETING_ONLINE", "ROLE_TEKNISI", "ROLE_DELIVERY")
                                        .requestMatchers("/api/v1/users/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "USERS")
                                        .requestMatchers("/api/v1/activity-logs/**", "/api/v1/audit/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "ACTIVITY_LOG", "ROLE_SPV_MARKETING", "ROLE_SPV_GUDANG", "ROLE_SPV_TEKNISI")
                                        
                                        // Laporan Omset Marketing
                                        .requestMatchers("/api/v1/sales/reports/marketing/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_ADMIN", "LAPORAN_OMSET")
                                        .requestMatchers("/api/v1/reminders/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "REMINDER")

                                        // LOKASI TRACKING: track vs view dipisah permission
                                        .requestMatchers(HttpMethod.POST, "/api/v1/locations/track", "/api/v1/locations/track/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "LOCATION_TRACK", "ROLE_DELIVERY")
                                        .requestMatchers(HttpMethod.GET, "/api/v1/locations/history", "/api/v1/locations/history/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER", "LOCATION_VIEW")

                                        // Catch-all sisanya
                                        .anyRequest().authenticated()
                                        )
                                .authenticationProvider(authenticationProvider())
                                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                                .formLogin(login -> login.disable())
                                .httpBasic(basic -> basic.disable());

                return http.build();
        }
        /** Uploads & Downloads: Static resources (foto memo/bukti, file update APK/MSIX) dapat diakses oleh mobile & web */
        @Bean
        @Order(3)
        public SecurityFilterChain uploadsSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .securityMatcher("/uploads/**", "/download/**")
                                .cors(Customizer.withDefaults())
                                .csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests(auth -> auth
                                                .anyRequest().permitAll())
                                .anonymous(Customizer.withDefaults())
                                .formLogin(login -> login.disable())
                                .httpBasic(basic -> basic.disable());
                return http.build();
        }

        /** Swagger: Hanya bisa diakses dari localhost / IP Internal Kantor (192.168.1.x) */
        @Bean
        @Order(4)
        public SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                                .cors(Customizer.withDefaults())
                                .csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                                                .access((authentication, context) -> {
                                                        if (isIpWhitelisted(context.getRequest())) {
                                                                return new org.springframework.security.authorization.AuthorizationDecision(true);
                                                        }
                                                        return new org.springframework.security.authorization.AuthorizationDecision(false);
                                                }))
                                .anonymous(anonymous -> anonymous.disable())
                                .formLogin(login -> login.disable())
                                .httpBasic(basic -> basic.disable());
                return http.build();
        }

        /** Web Dashboard (Session/Form Based) */
        @Bean
        @Order(5)
        public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .securityMatcher("/", "/check-db", "/login", "/logout", "/dashboard", "/login.html")
                                .cors(Customizer.withDefaults())
                                .csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/", "/check-db", "/login", "/logout", "/login.html").permitAll()
                                                .anyRequest().authenticated())
                                .authenticationProvider(authenticationProvider())
                                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                                .formLogin(login -> login
                                                .permitAll()
                                                .defaultSuccessUrl("/dashboard", true))
                                .logout(logout -> logout.permitAll())
                                .httpBasic(basic -> basic.disable());
                return http.build();
        }

/**
         * Catch-all security: Jaring pengaman terakhir.
         * SEMUA request yang tidak cocok dengan chain di atas (Order 1-5) akan DITOLAK (403).
         * Contoh: path tak dikenal, plugin scanner, bot, dsb.
         */
        @Bean
        @Order(6)
        public SecurityFilterChain catchAllSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .securityMatcher("/**")
                                .authorizeHttpRequests(auth -> auth
                                                .anyRequest().denyAll())
                                .csrf(csrf -> csrf.disable())
                                .formLogin(login -> login.disable())
                                .httpBasic(basic -> basic.disable());
                return http.build();
        }
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                // Domain production (whitelist) + IP lokal untuk development
                configuration.setAllowedOriginPatterns(List.of(
                    "https://api.anandamcomputer.com",
                    "https://admin.anandamcomputer.com",
                    "https://anandam.id",
                    "http://localhost:*",
                    "http://127.0.0.1:*",
                    "http://192.168.*.*:*"
                ));
                
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                
                configuration.setAllowedHeaders(List.of("*"));
                
                configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition", "Content-Type"));
                
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration); 
                return source;
        }
}