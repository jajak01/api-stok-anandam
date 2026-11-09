package com.stok.anandam.store.controller;

import com.stok.anandam.store.core.postgres.model.StatusServis;
import com.stok.anandam.store.core.postgres.model.TransaksiServis;
import com.stok.anandam.store.service.TransaksiServisService;
import com.stok.anandam.store.dto.CreateTransaksiRequest;
import com.stok.anandam.store.dto.UpdateStatusServisRequest;
import com.stok.anandam.store.dto.TransaksiServisResponse;
import com.stok.anandam.store.dto.RiwayatServisUserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transaksi-servis")
@RequiredArgsConstructor
public class TransaksiServisController {

    private final TransaksiServisService transaksiService;

    /**
     * Helper method untuk mengambil username dari token JWT yang sedang aktif.
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            return ((UserDetails) authentication.getPrincipal()).getUsername();
        }
        return null;
    }

    /**
     * GET /api/v1/transaksi-servis/filter-by-status
     * Mencari transaksi servis berdasarkan status dan keyword pencarian (noServis / namaPelanggan).
     * Mendukung pagination (page & size).
     *
     * @param status Status servis (required)
     * @param search Keyword pencarian noServis / namaPelanggan (optional)
     * @param page   Nomor halaman (default 0)
     * @param size   Jumlah data per halaman (default 20)
     */
    @GetMapping("/filter-by-status")
    public ResponseEntity<Map<String, Object>> getServisByStatus(
            @RequestParam StatusServis status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<TransaksiServisResponse> result = transaksiService.getServisByStatus(search, status, page, size);

        Map<String, Object> response = Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "number", result.getNumber(),
                "size", result.getSize()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/transaksi-servis/garansi/aktif
     * Mengembalikan daftar servisan yang masih dalam masa garansi (tglBatasGaransi > sekarang).
     * Mendukung pencarian (search) dan pagination.
     *
     * @param search Keyword pencarian noServis / namaPelanggan (optional)
     * @param page   Nomor halaman (default 0)
     * @param size   Jumlah data per halaman (default 20)
     */
    @GetMapping("/garansi/aktif")
    public ResponseEntity<Map<String, Object>> getGaransiAktif(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<TransaksiServisResponse> result = transaksiService.getServisGaransiAktif(search, page, size);

        Map<String, Object> response = Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "number", result.getNumber(),
                "size", result.getSize()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/transaksi-servis/garansi/expired
     * Mengembalikan daftar servisan yang masa garantinya sudah habis (tglBatasGaransi <= sekarang).
     * Mendukung pencarian (search) dan pagination.
     *
     * @param search Keyword pencarian noServis / namaPelanggan (optional)
     * @param page   Nomor halaman (default 0)
     * @param size   Jumlah data per halaman (default 20)
     */
    @GetMapping("/garansi/expired")
    public ResponseEntity<Map<String, Object>> getGaransiExpired(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<TransaksiServisResponse> result = transaksiService.getServisGaransiExpired(search, page, size);

        Map<String, Object> response = Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "number", result.getNumber(),
                "size", result.getSize()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/transaksi-servis/{id}
     * Mendapatkan detail transaksi servis berdasarkan ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransaksiServisResponse> getTransaksiById(@PathVariable UUID id) {
        TransaksiServisResponse response = transaksiService.getTransaksiById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/transaksi-servis/riwayat-pelanggan/{pelangganId}
     * Mendapatkan riwayat servis lengkap untuk seorang pelanggan.
     * Menampilkan data pelanggan, total jumlah servis, total biaya, 
     * total pendapatan bersih, dan detail setiap transaksi.
     *
     * @param pelangganId UUID pelanggan yang ingin dicek riwayatnya
     * @return RiwayatServisUserResponse berisi rekap dan detail transaksi
     */
    @GetMapping("/riwayat-pelanggan/{pelangganId}")
    public ResponseEntity<RiwayatServisUserResponse> getRiwayatServisPelanggan(@PathVariable UUID pelangganId) {
        RiwayatServisUserResponse response = transaksiService.getRiwayatServisByPelangganId(pelangganId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<TransaksiServisResponse> createTransaksi(@Valid @RequestBody CreateTransaksiRequest request) {
        String currentUsername = getCurrentUsername();
        TransaksiServisResponse response = transaksiService.buatTransaksiBaru(request, currentUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{transaksiId}/status")
    public ResponseEntity<TransaksiServisResponse> updateStatus(
            @PathVariable UUID transaksiId,
            @Valid @RequestBody UpdateStatusServisRequest request) { 
        
        String currentUsername = getCurrentUsername();
        TransaksiServisResponse response = transaksiService.updateStatusServis(transaksiId, request, currentUsername);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{transaksiId}")
    public ResponseEntity<TransaksiServisResponse> updateTransaksi(
            @PathVariable UUID transaksiId,
            @Valid @RequestBody com.stok.anandam.store.dto.UpdateTransaksiRequest request) {
        
        String currentUsername = getCurrentUsername();
        TransaksiServisResponse response = transaksiService.updateTransaksi(transaksiId, request, currentUsername);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/posisi")
    public ResponseEntity<Map<String, Object>> getByPosisi(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String posisi,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TransaksiServisResponse> result = transaksiService.getServisByPosisi(search, posisi, page, size);
        return ResponseEntity.ok(Map.of("content", result.getContent(), "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(), "number", result.getNumber(), "size", result.getSize()));
    }

    @GetMapping("/posisi/summary")
    public ResponseEntity<Map<String, Long>> getPosisiSummary() {
        return ResponseEntity.ok(transaksiService.getPosisiSummary());
    }

    @GetMapping("/{transaksiId}/posisi-log")
    public ResponseEntity<List<com.stok.anandam.store.dto.ServisPosisiLogResponse>> getPosisiLog(
            @PathVariable UUID transaksiId) {
        return ResponseEntity.ok(transaksiService.getPosisiLog(transaksiId));
    }

    @GetMapping("/klaim/rekap")
    public ResponseEntity<List<com.stok.anandam.store.dto.KlaimDistributorRekapResponse>> getRekapKlaim(
            @RequestParam(defaultValue = "AKTIF") String mode,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(transaksiService.getRekapKlaim(mode, search));
    }

    @GetMapping("/klaim/rekap/{namaDistributorNorm}")
    public ResponseEntity<Map<String, Object>> getDetailRekapKlaim(
            @PathVariable String namaDistributorNorm,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "SEMUA") String mode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TransaksiServisResponse> result = transaksiService.getKlaimDistributorDetail(namaDistributorNorm, search, mode, page, size);
        return ResponseEntity.ok(Map.of("content", result.getContent(), "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(), "number", result.getNumber(), "size", result.getSize()));
    }

    @PutMapping("/{transaksiId}/posisi")
    public ResponseEntity<TransaksiServisResponse> updatePosisi(
            @PathVariable UUID transaksiId,
            @Valid @RequestBody com.stok.anandam.store.dto.UpdatePosisiServisRequest request) {
        return ResponseEntity.ok(transaksiService.updatePosisiManual(transaksiId, request.getPosisi(), request.getAlasan(), getCurrentUsername()));
    }

    /**
     * GET /api/v1/klaim-distributor/by-transaksi/{transaksiId}
     * Mendapatkan data klaim distributor berdasarkan transaksi ID.
     * Mengembalikan 404 jika tidak ada klaim untuk transaksi tersebut.
     */
    @GetMapping("/klaim-distributor/by-transaksi/{transaksiId}")
    public ResponseEntity<com.stok.anandam.store.dto.KlaimDistributorResponse> getKlaimByTransaksiId(
            @PathVariable UUID transaksiId) {
        com.stok.anandam.store.core.postgres.model.KlaimDistributor klaim = transaksiService.getKlaimByTransaksiId(transaksiId);
        if (klaim == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(com.stok.anandam.store.dto.KlaimDistributorResponse.fromEntity(klaim));
    }

    @PostMapping("/{transaksiId}/klaim-distributor")
    public ResponseEntity<com.stok.anandam.store.core.postgres.model.KlaimDistributor> buatKlaimDistributor(
            @PathVariable UUID transaksiId,
            @jakarta.validation.Valid @RequestBody com.stok.anandam.store.dto.CreateKlaimRequest request) {
            
        String currentUsername = getCurrentUsername();
        com.stok.anandam.store.core.postgres.model.KlaimDistributor response = 
                transaksiService.buatKlaimDistributor(transaksiId, request, currentUsername);
                
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/klaim-distributor/{klaimId}")
    public ResponseEntity<com.stok.anandam.store.core.postgres.model.KlaimDistributor> updateDataKlaim(
            @PathVariable UUID klaimId,
            @RequestBody com.stok.anandam.store.dto.CreateKlaimRequest request) {
            
        String currentUsername = getCurrentUsername();
        com.stok.anandam.store.core.postgres.model.KlaimDistributor response = transaksiService.updateDataKlaim(
                klaimId, 
                request.getNamaDistributor(), 
                request.getAlamatDistributor(), 
                request.getBiayaKlaim(), 
                currentUsername
        );
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/klaim-distributor/{klaimId}/status")
    public ResponseEntity<com.stok.anandam.store.core.postgres.model.KlaimDistributor> updateStatusKlaim(
            @PathVariable UUID klaimId,
            @jakarta.validation.Valid @RequestBody com.stok.anandam.store.dto.UpdateKlaimStatusRequest request) {
            
        String currentUsername = getCurrentUsername();
        com.stok.anandam.store.core.postgres.model.KlaimDistributor response = transaksiService.updateStatusKlaimLogistik(
                klaimId, 
                request.getStatusBaru(), 
                request.getCatatanInternal(), 
                request.getCatatanPublik(), 
                request.getResiPengiriman(), 
                currentUsername
        );
        
        return ResponseEntity.ok(response);
    }
}
