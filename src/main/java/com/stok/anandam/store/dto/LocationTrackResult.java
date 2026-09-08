package com.stok.anandam.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Hasil proses satu titik lokasi (untuk batch/retry feedback). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationTrackResult {
    private Long id;
    private String clientEventId;
    /** true bila titik ini sudah pernah tercatat (idempotent duplicate). */
    private boolean duplicate;
}
