package com.stok.anandam.store.controller;

import com.stok.anandam.store.core.postgres.model.Stock;
import com.stok.anandam.store.dto.PagingResponse;
import com.stok.anandam.store.dto.StockSummaryByCategoryResponse;
import com.stok.anandam.store.dto.StockSummaryRowResponse;
import com.stok.anandam.store.dto.StockGroupedResponse;
import com.stok.anandam.store.dto.StokBadanGroupResponse;
import com.stok.anandam.store.dto.WebResponse;
import com.stok.anandam.store.service.AppSettingsService;
import com.stok.anandam.store.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.List;

@RestController
@RequestMapping(path = {"/api/v1/stocks", "/api/v1/stock"})
public class StockController {

        private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
                        "itemName", "itemCode", "kategoriNama", "kategoriItemcode", "finalStok", "grandTotal",
                        "warehouse", "id");

        @Autowired
        private StockService stockService;

        @Autowired
        private AppSettingsService appSettingsService;

        /**
         * GET persen hargaButtom (nilai fleksibel, default 3%).
         * Response: WebResponse<Map "percent">
         */
        @GetMapping("/harga-buttom-percent")
        public ResponseEntity<WebResponse<java.util.Map<String, Object>>> getHargaButtomPercent() {
                java.math.BigDecimal percent = appSettingsService.getHargaButtomPercent();
                return ResponseEntity.ok(WebResponse.<java.util.Map<String, Object>>builder()
                                .status(200)
                                .message("Success fetch harga buttom percent")
                                .data(Map.of("percent", percent))
                                .paging(null)
                                .build());
        }

        /**
         * PUT ubah persen hargaButtom (mis. 3 artinya +3% dari HPP).
         * Body: {"percent": 5}
         * Hanya ADMIN/MANAGER (dijaga di SecurityConfig).
         */
        @PutMapping("/harga-buttom-percent")
        public ResponseEntity<WebResponse<java.util.Map<String, Object>>> updateHargaButtomPercent(
                        @RequestBody java.util.Map<String, Object> body) {
                Object raw = body != null ? body.get("percent") : null;
                if (raw == null) {
                        throw new IllegalArgumentException("percent wajib diisi");
                }
                java.math.BigDecimal percent = new java.math.BigDecimal(raw.toString());
                java.math.BigDecimal saved = appSettingsService.setHargaButtomPercent(percent);
                return ResponseEntity.ok(WebResponse.<java.util.Map<String, Object>>builder()
                                .status(200)
                                .message("Harga buttom percent updated")
                                .data(Map.of("percent", saved))
                                .paging(null)
                                .build());
        }

        /**
         * GET list: filter search (nama/code), kategori (kategori_itemcode), warehouse.
         * Sort & paging.
         */
        @GetMapping({"", "/", "/grouped"})
        public ResponseEntity<WebResponse<Page<StockGroupedResponse>>> getAllStocks(
                        @RequestParam(name = "page", defaultValue = "0") int page,
                        @RequestParam(name = "size", defaultValue = "10") int size,
                        @RequestParam(name = "sortBy", defaultValue = "itemName") String sortBy,
                        @RequestParam(name = "direction", defaultValue = "asc") String direction,
                        @RequestParam(name = "search", required = false) String search,
                        @RequestParam(name = "categories", required = false) List<String> categories,
                        @RequestParam(name = "warehouse", required = false) String warehouse,
                        java.security.Principal principal) {
                String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "itemName";

                Page<StockGroupedResponse> stocks = stockService.getGroupedStocks(page, size, safeSortBy, direction,
                                search, categories, principal.getName());
                // Setelah search/filter, jika halaman yang diminta melebihi total halaman,
                // kembalikan halaman 0 agar list tidak kosong
                int totalPages = stocks.getTotalPages();
                if (totalPages > 0 && page >= totalPages) {
                        stocks = stockService.getGroupedStocks(0, size, safeSortBy, direction, search, categories, principal.getName());
                        page = 0;
                }

                PagingResponse paging = PagingResponse.builder()
                                .currentPage(page)
                                .totalPage(stocks.getTotalPages())
                                .size(size)
                                .totalItem(stocks.getTotalElements())
                                .build();

                return ResponseEntity.ok(WebResponse.<Page<StockGroupedResponse>>builder()
                                .status(200)
                                .message("Success Fetch Stock Data")
                                .data(stocks)
                                .paging(paging)
                                .build());
        }

        /**
         * Ringkasan stok per kategori: NAMA, STOK (total grand_total), PRESENTASE (%).
         * Baris terakhir = TOTAL (100%).
         */
        @GetMapping("/summary-by-category")
        public ResponseEntity<WebResponse<List<StockSummaryByCategoryResponse>>> getSummaryByCategory(
                        @RequestParam(name = "groupBy", defaultValue = "kategori_itemcode") String groupBy) {
                List<StockSummaryByCategoryResponse> data = stockService.getSummaryByCategory(groupBy);
                return ResponseEntity.ok(WebResponse.<List<StockSummaryByCategoryResponse>>builder()
                                .status(200)
                                .message("Success fetch stock summary by category")
                                .data(data)
                                .paging(null)
                                .build());
        }

        /**
         * Ringkasan stok dua level: parent (stok = jumlah children) + children. Contoh:
         * BRANDED → PCAIO, PCBU, PCMINI.
         */
        @GetMapping("/summary-by-category/hierarchy")
        public ResponseEntity<WebResponse<List<StockSummaryRowResponse>>> getSummaryByCategoryHierarchy() {
                List<StockSummaryRowResponse> data = stockService.getSummaryByCategoryHierarchy();
                return ResponseEntity.ok(WebResponse.<List<StockSummaryRowResponse>>builder()
                                .status(200)
                                .message("Success fetch stock summary by category (hierarchy)")
                                .data(data)
                                .paging(null)
                                .build());
        }

/**
         * Stok per badan: hitung purchase - sale per (badan, dep, item),
         * lalu terapkan redistribusi deficit non-ANC diambil dari ANC.
         * Output dikelompokkan per badan (6 badan: ANC,PDB,MGC,GBH,SSS,SGI).
         * Mirip dengan GET /api/stok di portal-tim-project.
         */
        @GetMapping("/badan")
        public ResponseEntity<WebResponse<List<StokBadanGroupResponse>>> getStokPerBadan() {
            List<StokBadanGroupResponse> data = stockService.getStokPerBadan();
            return ResponseEntity.ok(WebResponse.<List<StokBadanGroupResponse>>builder()
                    .status(200)
                    .message("Success fetch stok per badan")
                    .data(data)
                    .paging(null)
                    .build());
        }
/**
         * Stok per badan untuk SATU item (berdasarkan itemCode).
         */
        @GetMapping("/{itemCode}/badan")
        public ResponseEntity<WebResponse<Map<String, Integer>>> getStokPerBadanByItemCode(
                @PathVariable(name = "itemCode") String itemCode) {
            Map<String, Integer> data = stockService.getStokPerBadanByItemCode(itemCode);
            return ResponseEntity.ok(WebResponse.<Map<String, Integer>>builder()
                    .status(200)
                    .message("Success fetch stok per badan for item")
                    .data(data)
                    .paging(null)
                    .build());
        }
        // FITUR 3: Detail Stok (Get By ID)
        // FITUR 3: Detail Stok (Get By ID)
        @GetMapping("/{id}")
        public ResponseEntity<WebResponse<Stock>> getStockDetail(@PathVariable(name = "id") Long id, java.security.Principal principal) {
                Stock stock = stockService.getSingleStockDetail(id, principal.getName());
                
                // Hide HPP for detail if marketing
                if (stock != null) {
                    // Logic inside service is preferred, but Stock is an Entity. 
                    // Let's check StockService implementation first.
                }

                return ResponseEntity.ok(WebResponse.<Stock>builder()
                                .status(200)
                                .message("Success Fetch Stock Detail")
                                .data(stock)
                                .paging(null)
                                .build());
        }
}