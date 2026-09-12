package com.stok.anandam.store.controller;

import com.stok.anandam.store.core.postgres.model.Purchase;
import com.stok.anandam.store.dto.PagingResponse;
import com.stok.anandam.store.dto.PurchaseSummaryResponse;
import com.stok.anandam.store.dto.WebResponse;
import com.stok.anandam.store.service.ExcelExportService;
import com.stok.anandam.store.service.HutangService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HUTANG = daftar pembelian (kita membeli dari distributor) menurut status bayar.
 * Row-level & filter sama seperti halaman Pembelian.
 * Export Excel hanya untuk ADMIN/MANAGER (aturan di SecurityConfig).
 */
@RestController
@RequestMapping({"/api/v1/hutang", "/api/v1/hutangs"})
public class HutangController {

    @Autowired
    private HutangService hutangService;

    @Autowired
    private ExcelExportService excelExportService;

    @GetMapping
    public ResponseEntity<WebResponse<PurchaseSummaryResponse<Purchase>>> getHutang(
            Principal principal,
            @RequestParam(name = "tab", defaultValue = "UNPAID") String tab,
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

        boolean isPaid = "PAID".equalsIgnoreCase(tab);
        PurchaseSummaryResponse<Purchase> data = hutangService.getHutang(
                principal != null ? principal.getName() : null,
                isPaid, page, size, sortBy, direction, startDate, endDate,
                splitEmpCodes(empCode), categories, search, searchColumn);

        PagingResponse paging = PagingResponse.builder()
                .currentPage(page)
                .totalPage(data.getTotalPages())
                .size(size)
                .totalItem(data.getTotalElements())
                .build();

        return ResponseEntity.ok(WebResponse.<PurchaseSummaryResponse<Purchase>>builder()
                .status(200)
                .message("Success fetch hutang data")
                .data(data)
                .paging(paging)
                .build());
    }

    /** Export Excel hutang (tab/status + filter aktif). */
    @GetMapping("/export")
    public ResponseEntity<org.springframework.core.io.Resource> exportHutang(
            Principal principal,
            @RequestParam(name = "tab", defaultValue = "UNPAID") String tab,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            @RequestParam(name = "empCode", required = false) String empCode,
            @RequestParam(name = "categories", required = false) List<String> categories,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "searchColumn", required = false) String searchColumn) throws java.io.IOException {

        boolean isPaid = "PAID".equalsIgnoreCase(tab);
        List<Purchase> rows = hutangService.getAllHutangRows(
                principal != null ? principal.getName() : null,
                isPaid, startDate, endDate,
                splitEmpCodes(empCode), categories, search, searchColumn);
        byte[] bytes = excelExportService.exportPurchaseToExcel(rows);

        String filename = "hutang_" + (isPaid ? "PAID" : "UNPAID") + "_" + java.time.LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + filename)
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new org.springframework.core.io.ByteArrayResource(bytes));
    }

    @GetMapping({"/employee-codes", "/options"})
    public ResponseEntity<WebResponse<java.util.List<com.stok.anandam.store.dto.EmployeeOption>>> getEmployeeCodes(
            Principal principal) {
        String username = principal != null ? principal.getName() : null;
        java.util.List<com.stok.anandam.store.dto.EmployeeOption> codes = hutangService.getEmployeeOptions(username);
        return ResponseEntity.ok(WebResponse.<java.util.List<com.stok.anandam.store.dto.EmployeeOption>>builder()
                .status(200)
                .message("Success fetch employee codes")
                .data(codes)
                .paging(null)
                .build());
    }

    @GetMapping("/categories")
    public ResponseEntity<WebResponse<java.util.List<String>>> getCategories(Principal principal) {
        java.util.List<String> categories = hutangService.getCategories();
        return ResponseEntity.ok(WebResponse.<java.util.List<String>>builder()
                .status(200)
                .message("Success fetch categories")
                .data(categories)
                .paging(null)
                .build());
    }

    private List<String> splitEmpCodes(String empCode) {
        if (empCode == null || empCode.trim().isEmpty()) {
            return null;
        }
        return Arrays.stream(empCode.trim().split("\\s*,\\s*"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
