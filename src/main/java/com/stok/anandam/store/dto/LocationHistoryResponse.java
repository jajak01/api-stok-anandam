package com.stok.anandam.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Response riwayat lokasi untuk satu delivery/menu. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationHistoryResponse {
    private List<Point> content;
    private int totalPages;
    private long totalElements;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private Long id;
        private Long userId;
        private String username;
        private String deliveryId;
        private String menuCode;
        private Double latitude;
        private Double longitude;
        private Double accuracy;
        private LocalDateTime recordedAt;
        private LocalDateTime receivedAt;
        private LocalDateTime createdAt;
    }
}
