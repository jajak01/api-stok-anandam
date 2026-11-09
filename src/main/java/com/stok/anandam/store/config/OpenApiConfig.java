package com.stok.anandam.store.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

@Configuration
public class OpenApiConfig {

    /**
     * Opsional. Kosong = Swagger UI memakai origin halaman yang sedang dibuka
     * (localhost, IP LAN, atau domain reverse proxy). Ini mencegah error CORS /
     * "Failed to fetch" saat tombol "Try it out" dipakai.
     * Isi hanya jika ingin memaksa server tujuan tertentu, mis.
     * app.openapi.server-url=http://192.168.1.176:9099
     */
    @Value("${app.openapi.server-url:}")
    private String serverUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        final String bearerAuth = "bearerAuth";

        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Store Anandam API")
                        .version("1.0")
                        .description("API untuk aplikasi Stok Anandam - Hanya akses localhost / IP Internal Kantor. "
                                + "Semua error memakai wrapper {status, message, data, paging}. "
                                + "Kode error auth: INVALID_CREDENTIALS, ACCOUNT_DEACTIVATED, TOKEN_EXPIRED, "
                                + "INVALID_REFRESH_TOKEN, VALIDATION_ERROR, INTERNAL_ERROR.")
                        .contact(new Contact().name("Store Anandam")))
                .addSecurityItem(new SecurityRequirement().addList(bearerAuth))
                .components(new Components()
                        .addSecuritySchemes(bearerAuth,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Login via POST /api/v1/auth/login, lalu isi token di sini.")));

        // Server eksplisit hanya jika dikonfigurasi. Default: tanpa servers,
        // sehingga Swagger UI memakai origin halaman itu sendiri (same-origin).
        if (StringUtils.hasText(serverUrl)) {
            openAPI.servers(List.of(new Server()
                    .url(serverUrl)
                    .description("Server aktif")));
        }

        return openAPI;
    }
}