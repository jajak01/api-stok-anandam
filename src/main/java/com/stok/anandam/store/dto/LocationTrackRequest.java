package com.stok.anandam.store.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request pencatatan titik lokasi dari mobile (single point).
 * Server menurunkan user identity dari sesi/token, bukan dari request ini.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationTrackRequest {

    private String deliveryId;

    private String menuCode;

    @NotNull(message = "latitude wajib diisi")
    private Double latitude;

    @NotNull(message = "longitude wajib diisi")
    private Double longitude;

    private Double accuracy;

    /** Waktu perangkat memperoleh lokasi (GMT). */
    private LocalDateTime recordedAt;

    /** Idempotency key dari klien untuk retry/offline sync. */
    private String clientEventId;
}
