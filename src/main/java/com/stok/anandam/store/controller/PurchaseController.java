package com.stok.anandam.store.controller;

import com.stok.anandam.store.core.postgres.model.Purchase;
import com.stok.anandam.store.dto.PagingResponse;
import com.stok.anandam.store.dto.PurchaseSummaryResponse;
import com.stok.anandam.store.dto.WebResponse;
import com.stok.anandam.store.service.ExcelExportService;
import com.stok.anandam.store.service.PurchaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/purchases", "/api/v1/purchase"})
public class PurchaseController {

        @Autowired
        private PurchaseService purchaseService;

        @GetMapping
        public ResponseEntity<WebResponse<PurchaseSummaryResponse<Purchase>>> getPurchases(
                        java.security.Principal principal,
                        @RequestParam(name = "page", defaultValue = "0") int page,
                        @RequestParam(name = "size", defaultValue = "10") int size,
                        @RequestParam(name = "sortBy", defaultValue = "docDate") String sortBy,
                        @RequestParam(name = "dir", defaultValue = "desc") String dir,
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

                String username = principal != null ? principal.getName() : null;
                PurchaseSummaryResponse<Purchase> data = purchaseService.getPurchases(
                                page, size, sortBy, dir, startDate, endDate, empCodes, categories, search, searchColumn, username);
                if (data.getContent().isEmpty() && data.getTotalElements() > 0 && page > 0) {
                        data = purchaseService.getPurchases(0, size, sortBy, dir, startDate, endDate, empCodes, categories, search, searchColumn, username);
                        page = 0;
                }

                PagingResponse paging = PagingResponse.builder()
                                .currentPage(page)
                                .totalPage(data.getTotalPages())
                                .size(size)
                                .totalItem(data.getTotalElements())
                                .build();

                return ResponseEntity.ok(WebResponse.<PurchaseSummaryResponse<Purchase>>builder()
                                .status(200)
                                .message("Success fetch purchase data")
                                .data(data)
                                .paging(paging)
                                .build());
        }

        @GetMapping("/categories")
        public ResponseEntity<WebResponse<java.util.List<String>>> getCategories() {
                java.util.List<String> categories = purchaseService.getDeptCodes();
                return ResponseEntity.ok(WebResponse.<java.util.List<String>>builder()
                                .status(200)
                                .message("Success fetch categories")
                                .data(categories)
                                .build());
        }

        @Autowired
        private ExcelExportService excelExportService;

        @GetMapping("/export")
        public ResponseEntity<org.springframework.core.io.Resource> exportToExcel(
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

                java.util.List<Purchase> data = purchaseService.getAllPurchasesForExport(startDate, endDate, empCodes, categories, search, searchColumn);
                byte[] bytes = excelExportService.exportPurchaseToExcel(data);

                String filename = "purchases_" + java.time.LocalDate.now() + ".xlsx";
                return ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=" + filename)
                                .contentType(org.springframework.http.MediaType.parseMediaType(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                .body(new org.springframework.core.io.ByteArrayResource(bytes));
        }
}