package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.PenjadwalanKonfirmasi;
import com.stok.anandam.store.core.postgres.model.Permission;
import com.stok.anandam.store.core.postgres.model.Role;
import com.stok.anandam.store.core.postgres.model.User;
import com.stok.anandam.store.core.postgres.model.UserLocation;
import com.stok.anandam.store.core.postgres.repository.PenjadwalanKonfirmasiRepository;
import com.stok.anandam.store.core.postgres.repository.UserLocationRepository;
import com.stok.anandam.store.dto.LocationHistoryResponse;
import com.stok.anandam.store.dto.LocationTrackRequest;
import com.stok.anandam.store.dto.LocationTrackResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Location tracking service (plan 19-27, 40):
 * - Menyimpan titik lokasi user_locations dgn identitas user dari sesi (bukan client).
 * - Mengharuskan permission LOCATION_TRACK untuk menulis, LOCATION_VIEW untuk membaca riwayat.
 * - Idempotency via clientEventId (offline queue retry aman).
 * - User DELIVERY hanya boleh merekam lokasi utk delivery/task yang menjadi tanggung jawabnya.
 */
@Service
public class LocationTrackingService {

    @Autowired
    private UserLocationRepository userLocationRepository;

    @Autowired
    private PenjadwalanKonfirmasiRepository penjadwalanRepo;

    @Autowired
    private UserAuthorizationService userAuthorizationService;

    /** Cek permission & dapatkan actor. */
    private User requireActor(String username, String permission) {
        User aktor = userAuthorizationService.requireUser(username);
        if (!userAuthorizationService.hasPermission(aktor, permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Anda tidak memiliki akses " + permission);
        }
        return aktor;
    }

    /**
     * Validasi delivery/task: user DELIVERY hanya boleh mengirim lokasi utk memo/task
     * yang dijadwalkan kepadanya (personelId = user.id). Role lain (MANAGER/ADMIN/SPV)
     * dianggap berhak mengikuti delivery (lokasi.track diberikan manager).
     */
    private void assertDeliveryAccess(User aktor, String deliveryId) {
        if (aktor.getRole() == Role.MANAGER || aktor.getRole() == Role.ADMIN
                || aktor.getRole() == Role.SPV_MARKETING || aktor.getRole() == Role.SPV_GUDANG) {
            return;
        }
        if (deliveryId == null || deliveryId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "deliveryId wajib diisi untuk merekam lokasi pengiriman");
        }
        List<PenjadwalanKonfirmasi> assigned = penjadwalanRepo.findByPersonelIdAndDeletedAtIsNull(aktor.getId());
        if (assigned == null) {
            assigned = List.of();
        }
        boolean found = false;
        // deliveryId bisa berupa memo UUID atau id task numeric.
        try {
            UUID memoId = UUID.fromString(deliveryId.trim());
            found = assigned.stream().anyMatch(t ->
                    (t.getMemo() != null && t.getMemo().getId().equals(memoId)) ||
                    (t.getManifestMemos() != null && t.getManifestMemos().stream()
                            .anyMatch(m -> m.getId().equals(memoId))));
        } catch (IllegalArgumentException notUuid) {
            try {
                Long taskId = Long.valueOf(deliveryId.trim());
                found = assigned.stream().anyMatch(t -> t.getId().equals(taskId));
            } catch (NumberFormatException ignored) {
                found = false;
            }
        }
        if (!found) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Delivery/task tidak termasuk tanggung jawab user ini");
        }
    }

    /** Simpan satu titik; jika clientEventId sudah pernah ada, kembalikan data lama (idempotent). */
    @Transactional
    public LocationTrackResult track(String username, LocationTrackRequest req) {
        User aktor = requireActor(username, Permission.LOCATION_TRACK);
        if (req.getLatitude() == null || req.getLongitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude/longitude wajib diisi");
        }
        assertDeliveryAccess(aktor, req.getDeliveryId());

        if (req.getClientEventId() != null && !req.getClientEventId().isBlank()) {
            Optional<UserLocation> existing = userLocationRepository.findByClientEventId(req.getClientEventId().trim());
            if (existing.isPresent()) {
                return LocationTrackResult.builder()
                        .id(existing.get().getId())
                        .clientEventId(existing.get().getClientEventId())
                        .duplicate(true)
                        .build();
            }
        }

        UserLocation loc = UserLocation.builder()
                .userId(aktor.getId())
                .username(aktor.getUsername())
                .deliveryId(req.getDeliveryId())
                .menuCode(req.getMenuCode())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .accuracy(req.getAccuracy())
                .clientEventId(req.getClientEventId() != null ? req.getClientEventId().trim() : null)
                .recordedAt(req.getRecordedAt() != null ? req.getRecordedAt() : LocalDateTime.now())
                .build();
        UserLocation saved = userLocationRepository.save(loc);
        return LocationTrackResult.builder()
                .id(saved.getId())
                .clientEventId(saved.getClientEventId())
                .duplicate(false)
                .build();
    }

    /** Batch upload (offline queue flush). Idempotent per baris. */
    @Transactional
    public List<LocationTrackResult> trackBatch(String username, List<LocationTrackRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<LocationTrackResult> results = new ArrayList<>();
        for (LocationTrackRequest req : requests) {
            try {
                results.add(track(username, req));
            } catch (ResponseStatusException e) {
                // Baris invalid tidak menggagalkan seluruh batch.
                results.add(LocationTrackResult.builder()
                        .clientEventId(req != null ? req.getClientEventId() : null)
                        .duplicate(true)
                        .build());
            }
        }
        return results;
    }

    /** Riwayat lokasi per delivery/menu. Butuh LOCATION_VIEW. */
    @Transactional(readOnly = true)
    public LocationHistoryResponse history(String username, String deliveryId,
                                           int page, int size, LocalDateTime from, LocalDateTime to) {
        User aktor = requireActor(username, Permission.LOCATION_VIEW);
        if (deliveryId == null || deliveryId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deliveryId wajib diisi");
        }
        assertDeliveryAccess(aktor, deliveryId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordedAt"));
        List<UserLocation> rows;
        Page<UserLocation> pageResult;
        if (from != null && to != null) {
            rows = userLocationRepository.findByDeliveryIdAndRecordedAtBetweenOrderByRecordedAtAsc(deliveryId.trim(), from, to);
            pageResult = null;
        } else {
            pageResult = userLocationRepository.findByDeliveryIdOrderByRecordedAtDesc(deliveryId.trim(), pageable);
            rows = pageResult.getContent();
        }

        List<LocationHistoryResponse.Point> content = new ArrayList<>();
        for (UserLocation l : rows) {
            content.add(LocationHistoryResponse.Point.builder()
                    .id(l.getId())
                    .userId(l.getUserId())
                    .username(l.getUsername())
                    .deliveryId(l.getDeliveryId())
                    .menuCode(l.getMenuCode())
                    .latitude(l.getLatitude())
                    .longitude(l.getLongitude())
                    .accuracy(l.getAccuracy())
                    .recordedAt(l.getRecordedAt())
                    .receivedAt(l.getReceivedAt())
                    .createdAt(l.getCreatedAt())
                    .build());
        }
        return LocationHistoryResponse.builder()
                .content(content)
                .totalPages(pageResult != null ? pageResult.getTotalPages() : 1)
                .totalElements(pageResult != null ? pageResult.getTotalElements() : content.size())
                .build();
    }
}
