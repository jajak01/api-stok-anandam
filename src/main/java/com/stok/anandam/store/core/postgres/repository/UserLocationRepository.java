package com.stok.anandam.store.core.postgres.repository;

import com.stok.anandam.store.core.postgres.model.UserLocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserLocationRepository extends JpaRepository<UserLocation, Long> {

    List<UserLocation> findByDeliveryIdOrderByRecordedAtAsc(String deliveryId);

    Page<UserLocation> findByDeliveryIdOrderByRecordedAtDesc(String deliveryId, Pageable pageable);

    Page<UserLocation> findByUserIdOrderByRecordedAtDesc(Long userId, Pageable pageable);

    boolean existsByClientEventId(String clientEventId);

    Optional<UserLocation> findByClientEventId(String clientEventId);

    List<UserLocation> findByDeliveryIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            String deliveryId, LocalDateTime start, LocalDateTime end);
}
