package com.stok.anandam.store.controller;

import com.stok.anandam.store.dto.SuggestItemResponse;
import com.stok.anandam.store.dto.WebResponse;
import com.stok.anandam.store.service.ServisMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint saran untuk form Servis v3.
 *
 * <p>Dua kosakata distributor sengaja dipisah:
 * <ul>
 *   <li>{@code /supplier/suggest} — supplier pembelian (par_name), internal</li>
 *   <li>{@code /distributor/suggest} — tujuan klaim (nama pendek dari riwayat klaim)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/servis")
@RequiredArgsConstructor
public class ServisMasterController {

    private final ServisMasterService servisMasterService;

    @GetMapping("/barang/suggest")
    public ResponseEntity<WebResponse<List<SuggestItemResponse>>> suggestBarang(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {

        List<SuggestItemResponse> data = servisMasterService.suggestBarang(q, limit);
        return ResponseEntity.ok(WebResponse.<List<SuggestItemResponse>>builder()
                .status(200)
                .message("Success Fetch Saran Barang")
                .data(data)
                .build());
    }

    @GetMapping("/supplier/suggest")
    public ResponseEntity<WebResponse<List<SuggestItemResponse>>> suggestSupplier(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "namaBarang", required = false, defaultValue = "") String namaBarang,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {

        List<SuggestItemResponse> data = servisMasterService.suggestSupplier(q, namaBarang, limit);
        return ResponseEntity.ok(WebResponse.<List<SuggestItemResponse>>builder()
                .status(200)
                .message("Success Fetch Saran Supplier")
                .data(data)
                .build());
    }

    @GetMapping("/distributor/suggest")
    public ResponseEntity<WebResponse<List<SuggestItemResponse>>> suggestDistributor(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {

        List<SuggestItemResponse> data = servisMasterService.suggestDistributor(q, limit);
        return ResponseEntity.ok(WebResponse.<List<SuggestItemResponse>>builder()
                .status(200)
                .message("Success Fetch Saran Distributor")
                .data(data)
                .build());
    }
}
