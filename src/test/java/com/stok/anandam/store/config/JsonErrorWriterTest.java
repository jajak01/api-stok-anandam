package com.stok.anandam.store.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonErrorWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonErrorWriter writer = new JsonErrorWriter(objectMapper);

    @Test
    void writesStandardWrapperForFilterLevelError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/heartbeat");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, HttpStatus.UNAUTHORIZED,
                "TOKEN_EXPIRED", "Sesi habis atau token tidak valid");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());

        Map<?, ?> body = objectMapper.readValue(response.getContentAsByteArray(), Map.class);
        assertThat(body.get("status")).isEqualTo(401);
        assertThat(body.get("message")).isEqualTo("Sesi habis atau token tidak valid");

        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertThat(data.get("code")).isEqualTo("TOKEN_EXPIRED");
        assertThat(data.get("error")).isEqualTo("Unauthorized");
        assertThat(data.get("path")).isEqualTo("/api/v1/auth/heartbeat");
        assertThat(data.get("status")).isEqualTo(401);
        assertThat(data.get("timestamp")).isNotNull();
    }
}
