package com.stok.anandam.store.exception;

import com.stok.anandam.store.dto.LoginUserRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/test/conflict")
        String conflict() {
            throw new IllegalStateException("Nama barang dan supplier terkunci karena serial number telah terverifikasi.");
        }

        @GetMapping("/test/bad-credentials")
        String badCredentials() {
            throw new BadCredentialsException("Bad credentials");
        }

        @GetMapping("/test/disabled")
        String disabled() {
            throw new DisabledException("User is disabled");
        }

        @GetMapping("/test/invalid-refresh")
        String invalidRefresh() {
            throw new InvalidRefreshTokenException("Refresh token tidak ditemukan atau tidak valid");
        }

        @GetMapping("/test/unexpected")
        String unexpected() {
            throw new RuntimeException("boom");
        }

        @PostMapping("/test/login")
        String login(@Valid @RequestBody LoginUserRequest request) {
            return "ok";
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void lockedNoteEditMapsTo409Conflict() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.data.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Nama barang dan supplier terkunci karena serial number telah terverifikasi."));
    }

    @Test
    void badCredentialsMapsTo401InvalidCredentials() throws Exception {
        mockMvc.perform(get("/test/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Username atau password salah"))
                .andExpect(jsonPath("$.data.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.data.error").value("Unauthorized"))
                .andExpect(jsonPath("$.data.path").value("/test/bad-credentials"))
                .andExpect(jsonPath("$.data.status").value(401));
    }

    @Test
    void disabledAccountMapsTo403AccountDeactivated() throws Exception {
        mockMvc.perform(get("/test/disabled"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(
                        "Akun Anda telah dinonaktifkan. Silakan hubungi admin."))
                .andExpect(jsonPath("$.data.code").value("ACCOUNT_DEACTIVATED"));
    }

    @Test
    void invalidRefreshTokenMapsTo401InvalidRefreshToken() throws Exception {
        mockMvc.perform(get("/test/invalid-refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(
                        "Refresh token tidak ditemukan atau tidak valid"))
                .andExpect(jsonPath("$.data.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void validationErrorUsesWrapperAndFieldErrors() throws Exception {
        mockMvc.perform(post("/test/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.fieldErrors.username").value("Username wajib diisi"))
                .andExpect(jsonPath("$.data.fieldErrors.password").value("Password wajib diisi"));
    }

    @Test
    void unexpectedExceptionStillMapsTo500InternalError() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.data.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Terjadi kesalahan internal pada server. Silakan hubungi admin."));
    }
}
