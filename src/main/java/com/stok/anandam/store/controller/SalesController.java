package com.stok.anandam.store.controller;

import com.stok.anandam.store.core.postgres.model.Sales;
import com.stok.anandam.store.dto.MarketingItemDetailResponse;
import com.stok.anandam.store.dto.MarketingNotaDetailResponse;
import com.stok.anandam.store.dto.MarketingSalesSummaryResponse;
import com.stok.anandam.store.dto.MarketingTimelineResponse;
import com.stok.anandam.store.dto.PagingResponse;
import com.stok.anandam.store.dto.ProfitabilityResponse;
import com.stok.anandam.store.dto.ProfitabilityRowResponse;
import com.stok.anandam.store.dto.SalesSummaryResponse;
import com.stok.anandam.store.dto.WebResponse;
import com.stok.anandam.store.service.ExcelExportService;
import com.stok.anandam.store.service.SalesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping({ "/api/v1/sales", "/api/v1/sale" })
public class SalesController {

        @Autowired
        private SalesService salesService;

        @GetMapping
        public ResponseEntity<WebResponse<SalesSummaryResponse<Sales>>> getAllSales(
                        java.security.Principal principal,
                        @RequestParam(name = "page", defaultValue = "0") int page,
                        @RequestParam(name = "size", defaultValue = "10") int size,
                        @RequestParam(name = "sortBy", defaultValue = "docDate") String sortBy,
                        @RequestParam(name = "direction", defaultValue = "desc") String direction,
                        @RequestParam(name = "startDate", required = false) String startDate,
                        @RequestParam(name = "endDate", required = false) String endDate,
                        @RequestParam(name = "empCode", required = false) String empCode,
                        @RequestParam(name = "categories", required = false) List<String> categories,
                        @RequestParam(name = "search", required = false) String search,
                        @RequestParam(name = "searchColumn", required = false) String searchColumn) {

                // Convert comma-separated empCode to list
                List<String> empCodes = null;
                if (empCode != null && !empCode.trim().isEmpty()) {
                        empCodes = Arrays.asList(empCode.trim().split("\\s*,\\s*"));
                        empCodes = empCodes.stream()
                                .filter(s -> !s.isEmpty())
                                .collect(java.util.stream.Collectors.toList());
                        if (empCodes.isEmpty()) empCodes = null;
                }

                SalesSummaryResponse<Sales> data = salesService.getSales(
                                page, size, sortBy, direction, startDate, endDate, empCodes, categories, search, searchColumn,
                                principal != null ? principal.getName() : null);
                if (data.getContent().isEmpty() && data.getTotalElements() > 0 && page > 0) {
                        data = salesService.getSales(0, size, sortBy, direction, startDate, endDate, empCodes, categories, search, searchColumn,
                                        principal != null ? principal.getName() : null);
                        page = 0;
                }

                PagingResponse paging = PagingResponse.builder()
                                .currentPage(page)
                                .totalPage(data.getTotalPages())
                                .size(size)
                                .totalItem(data.getTotalElements())
                                .build();

                return ResponseEntity.ok(WebResponse.<SalesSummaryResponse<Sales>>builder()
                                .status(200)
                                .message("Success Fetch Sales Data")
                                .data(data)
                                .paging(paging)
                                .build());
        }

        @GetMapping("/employee-codes")
        public ResponseEntity<WebResponse<java.util.List<com.stok.anandam.store.dto.EmployeeOption>>> getEmployeeCodes() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                System.out.println("User Authorities: " + auth.getAuthorities());
                java.util.List<com.stok.anandam.store.dto.EmployeeOption> codes = salesService.getEmployeeCodes();
                return ResponseEntity
                                .ok(WebResponse.<java.util.List<com.stok.anandam.store.dto.EmployeeOption>>builder()
                                                .status(200)
                                                .message("Success fetch employee codes")
                                                .data(codes)
                                                .paging(null)
                                                .build());
        }

        @GetMapping("/categories")
        public ResponseEntity<WebResponse<java.util.List<String>>> getCategories() {
                java.util.List<String> categories = salesService.getDeptCodes();
                return ResponseEntity.ok(WebResponse.<java.util.List<String>>builder()
                                .status(200)
                                .message("Success fetch categories")
                                .data(categories)
                                .build());
        }

        // Laporan Profitabilitas Penjualan (HPP & Laba Kotor per barang)
        @GetMapping({ "/profitabilitas", "/profitability" })
        public ResponseEntity<WebResponse<ProfitabilityResponse<ProfitabilityRowResponse>>> getProfitability(
                        java.security.Principal principal,
                        @RequestParam(name = "startDate", required = false) String startDate,
                        @RequestParam(name = "endDate", required = false) String endDate,
                        @RequestParam(name = "empCode", required = false) String empCode,
                        @RequestParam(name = "categories", required = false) List<String> categories,
                        @RequestParam(name = "search", required = false) String search,
                        @RequestParam(name = "searchColumn", required = false) String searchColumn) {

                List<String> empCodes = null;
                if (empCode != null && !empCode.trim().isEmpty()) {
                        empCodes = Arrays.asList(empCode.trim().split("\\s*,\\s*"));
                        empCodes = empCodes.stream()
                                        .filter(s -> !s.isEmpty())
                                        .collect(java.util.stream.Collectors.toList());
                        if (empCodes.isEmpty()) empCodes = null;
                }

                ProfitabilityResponse<ProfitabilityRowResponse> data = salesService.getProfitability(
                                startDate, endDate, empCodes, categories, search, searchColumn,
                                principal != null ? principal.getName() : null);

                return ResponseEntity.ok(WebResponse.<ProfitabilityResponse<ProfitabilityRowResponse>>builder()
                                .status(200)
                                .message("Success fetch Profitability Data")
                                .data(data)
                                .paging(null)
                                .build());
        }

        @Autowired
        private ExcelExportService excelExportService;

        @GetMapping("/export")
        public ResponseEntity<org.springframework.core.io.Resource> exportToExcel(
                        java.security.Principal principal,
                        @RequestParam(name = "startDate", required = false) String startDate,
                        @RequestParam(name = "endDate", required = false) String endDate,
                        @RequestParam(name = "empCode", required = false) String empCode,
                        @RequestParam(name = "categories", required = false) List<String> categories,
                        @RequestParam(name = "search", required = false) String search,
                        @RequestParam(name = "searchColumn", required = false) String searchColumn) throws java.io.IOException {

                // Convert comma-separated empCode to list
                List<String> empCodes = null;
                if (empCode != null && !empCode.trim().isEmpty()) {
                        empCodes = Arrays.asList(empCode.trim().split("\\s*,\\s*"));
                        empCodes = empCodes.stream()
                                .filter(s -> !s.isEmpty())
                                .collect(java.util.stream.Collectors.toList());
                        if (empCodes.isEmpty()) empCodes = null;
                }

                java.util.List<Sales> data = salesService.getAllSalesForExport(startDate, endDate, empCodes, categories, search, searchColumn,
                                principal != null ? principal.getName() : null);
                byte[] bytes = excelExportService.exportSalesToExcel(data);

                String filename = "sales_" + java.time.LocalDate.now() + ".xlsx";
                return ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=" + filename)
                                .contentType(org.springframework.http.MediaType.parseMediaType(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                .body(new org.springframework.core.io.ByteArrayResource(bytes));
        }
        // ==================== LAPORAN OMSET & MARGIN PER MARKETING ====================

        /**
         * Ringkasan omzet + margin kotor per marketing.
         * Filter periode: period=DAY|WEEK|MONTH|YEAR & date=YYYY-MM-DD.
         * empCode opsional, bisa multi (dipisah koma) untuk membatasi marketing tertentu.
         */
        @GetMapping("/reports/marketing/overview")
        public ResponseEntity<WebResponse<MarketingSalesSummaryResponse>> getMarketingSalesOverview(
                        @RequestParam(name = "period", defaultValue = "DAY") String period,
                        @RequestParam(name = "date", required = false) String date,
                        @RequestParam(name = "startDate", required = false) String startDate,
                        @RequestParam(name = "endDate", required = false) String endDate,
                        @RequestParam(name = "empCode", required = false) String empCode) {

                MarketingSalesSummaryResponse data = salesService.getMarketingSalesSummary(
                                period,
                                parseDate(date),
                                parseDate(startDate),
                                parseDate(endDate),
                                splitEmpCodes(empCode));

                return ResponseEntity.ok(WebResponse.<MarketingSalesSummaryResponse>builder()
                                .status(200)
                                .message("Success fetch marketing sales report")
                                .data(data)
                                .paging(null)
                                .build());
        }

        /**
         * Detail per nota (doc) untuk satu marketing pada periode.
         */
        @GetMapping("/reports/marketing/notas")
        public ResponseEntity<WebResponse<MarketingNotaDetailResponse>> getMarketingSalesNotas(
                        @RequestParam(name = "period", defaultValue = "DAY") String period,
                        @RequestParam(name = "date", required = false) String date,
                        @RequestParam(name = "startDate", required = false) String startDate,
                        @RequestParam(name = "endDate", required = false) String endDate,
                        @RequestParam(name = "empCode", required = false) String empCode) {

                MarketingNotaDetailResponse data = salesService.getMarketingSalesNotaDetail(
                                period, parseDate(date), parseDate(startDate),
                                parseDate(endDate), empCode);

                return ResponseEntity.ok(WebResponse.<MarketingNotaDetailResponse>builder()
                                .status(200)
                                .message("Success fetch marketing nota detail")
                                .data(data)
                                .paging(null)
                                .build());
        }

        /**
         * Detail per barang (item) untuk satu marketing pada periode.
         */
        @GetMapping("/reports/marketing/items")
        public ResponseEntity<WebResponse<MarketingItemDetailResponse>> getMarketingSalesItems(
                        @RequestParam(name = "period", defaultValue = "DAY") String period,
                        @RequestParam(name = "date", required = false) String date,
                        @RequestParam(name = "startDate", required = false) String startDate,
                        @RequestParam(name = "endDate", required = false) String endDate,
                        @RequestParam(name = "empCode", required = false) String empCode) {

                MarketingItemDetailResponse data = salesService.getMarketingSalesItemDetail(
                                period, parseDate(date), parseDate(startDate),
                                parseDate(endDate), empCode);

                return ResponseEntity.ok(WebResponse.<MarketingItemDetailResponse>builder()
                                .status(200)
                                .message("Success fetch marketing item detail")
                                .data(data)
                                .paging(null)
                                .build());
        }

        /**
         * Titik grafik deret waktu (timeline) agregat untuk marketing pada periode.
         */
        @GetMapping("/reports/marketing/timeline")
        public ResponseEntity<WebResponse<MarketingTimelineResponse>> getMarketingSalesTimeline(
                        @RequestParam(name = "period", defaultValue = "DAY") String period,
                        @RequestParam(name = "date", required = false) String date,
                        @RequestParam(name = "startDate", required = false) String startDate,
                        @RequestParam(name = "endDate", required = false) String endDate,
                        @RequestParam(name = "empCode", required = false) String empCode) {

                MarketingTimelineResponse data = salesService.getMarketingSalesTimeline(
                                period,
                                parseDate(date),
                                parseDate(startDate),
                                parseDate(endDate),
                                splitEmpCodes(empCode));

                return ResponseEntity.ok(WebResponse.<MarketingTimelineResponse>builder()
                                .status(200)
                                .message("Success fetch marketing sales timeline")
                                .data(data)
                                .paging(null)
                                .build());
        }

        /** Parse tanggal opsional; null jika kosong. */
        private java.time.LocalDate parseDate(String date) {
                return (date == null || date.isBlank()) ? null : java.time.LocalDate.parse(date);
        }

        /** Ubah string kode marketing (koma) menjadi list; null jika kosong. */
        private java.util.List<String> splitEmpCodes(String empCode) {
                if (empCode == null || empCode.trim().isEmpty())
                        return null;
                return java.util.Arrays.stream(empCode.trim().split("\\s*,\\s*"))
                                .filter(s -> !s.isEmpty())
                                .collect(java.util.stream.Collectors.toList());
        }
}