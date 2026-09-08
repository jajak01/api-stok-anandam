package com.stok.anandam.store.core.postgres.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_locations", indexes = {
    @Index(name = "idx_user_loc_delivery", columnList = "delivery_id"),
    @Index(name = "idx_user_loc_user", columnList = "user_id"),
    @Index(name = "idx_user_loc_client_event", columnList = "client_event_id")
})
public class UserLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "delivery_id", length = 100)
    private String deliveryId;

    @Column(name = "menu_code", length = 50)
    private String menuCode;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "accuracy")
    private Double accuracy;

    @Column(name = "client_event_id", length = 100, unique = true)
    private String clientEventId;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.receivedAt = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.recordedAt == null) {
            this.recordedAt = LocalDateTime.now();
        }
    }
}
