package com.stok.anandam.store.controller;

import com.stok.anandam.store.dto.AddressResponse;
import com.stok.anandam.store.dto.LocationHistoryResponse;
import com.stok.anandam.store.dto.LocationTrackRequest;
import com.stok.anandam.store.dto.LocationTrackResult;
import com.stok.anandam.store.dto.PagingResponse;
import com.stok.anandam.store.dto.WebResponse;
import com.stok.anandam.store.service.LocationService;
import com.stok.anandam.store.service.LocationTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;
    private final LocationTrackingService locationTrackingService;

    public LocationController(LocationService locationService,
                              LocationTrackingService locationTrackingService) {
        this.locationService = locationService;
        this.locationTrackingService = locationTrackingService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<AddressResponse>> search(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        List<AddressResponse> results = locationService.searchAddress(q);
        return ResponseEntity.ok(results);
    }

    /** Kirim satu titik lokasi delivery (LOCATION_TRACK). */
    @PostMapping("/track")
    public ResponseEntity<WebResponse<LocationTrackResult>> track(
            Principal principal,
            @RequestBody LocationTrackRequest request) {
        LocationTrackResult result = locationTrackingService.track(
                principal != null ? principal.getName() : null, request);
        return ResponseEntity.ok(WebResponse.<LocationTrackResult>builder()
                .status(200)
                .message("Location tracked")
                .data(result)
                .paging(null)
                .build());
    }

    /** Kirim batch titik lokasi (offline queue flush / heartbeat cadence). */
    @PostMapping("/track/batch")
    public ResponseEntity<WebResponse<List<LocationTrackResult>>> trackBatch(
            Principal principal,
            @RequestBody List<LocationTrackRequest> requests) {
        List<LocationTrackResult> results = locationTrackingService.trackBatch(
                principal != null ? principal.getName() : null, requests);
        return ResponseEntity.ok(WebResponse.<List<LocationTrackResult>>builder()
                .status(200)
                .message("Location batch tracked")
                .data(results)
                .paging(null)
                .build());
    }

    /** Riwayat lokasi per delivery (LOCATION_VIEW). */
    @GetMapping("/history")
    public ResponseEntity<WebResponse<LocationHistoryResponse>> history(
            Principal principal,
            @RequestParam(name = "deliveryId") String deliveryId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {

        LocalDateTime fromDt = (from != null && !from.isBlank()) ? LocalDateTime.parse(from) : null;
        LocalDateTime toDt = (to != null && !to.isBlank()) ? LocalDateTime.parse(to) : null;

        LocationHistoryResponse data = locationTrackingService.history(
                principal != null ? principal.getName() : null,
                deliveryId, page, size, fromDt, toDt);

        PagingResponse paging = PagingResponse.builder()
                .currentPage(page)
                .totalPage(data.getTotalPages())
                .size(size)
                .totalItem(data.getTotalElements())
                .build();

        return ResponseEntity.ok(WebResponse.<LocationHistoryResponse>builder()
                .status(200)
                .message("Success fetch location history")
                .data(data)
                .paging(paging)
                .build());
    }
}
