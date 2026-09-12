package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.Permission;
import com.stok.anandam.store.core.postgres.model.Purchase;
import com.stok.anandam.store.core.postgres.model.User;
import com.stok.anandam.store.core.postgres.repository.PurchaseRepository;
import com.stok.anandam.store.dto.PurchaseSummaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * HUTANG = data pembelian (kita membeli dari distributor) menurut status bayar.
 * Row-level & filter sama seperti halaman Pembelian (tanggal, empCode, kategori, search).
 * Di-gate oleh permission PEMBELIAN.
 */
@Service
public class HutangService {

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private UserAuthorizationService userAuthorizationService;

    public PurchaseSummaryResponse<Purchase> getHutang(
            String username, boolean isPaid, int page, int size, String sortBy, String dir,
            String startDateStr, String endDateStr, List<String> empCodes,
            List<String> categories, String search, String searchColumn) {

        User aktor = userAuthorizationService.requireUser(username);
        if (!userAuthorizationService.hasPermission(aktor, Permission.PEMBELIAN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses HUTANG");
        }

        List<String> scopeFilter = userAuthorizationService.effectiveScopeFilter(aktor, empCodes);
        if (scopeFilter != null && scopeFilter.isEmpty()) {
            return emptySummary();
        }

        LocalDate start = (startDateStr != null && !startDateStr.isBlank())
                ? LocalDate.parse(startDateStr)
                : LocalDate.of(2016, 1, 1);
        LocalDate end = (endDateStr != null && !endDateStr.isBlank())
                ? LocalDate.parse(endDateStr)
                : LocalDate.now();

        Sort.Direction direction = dir != null && dir.equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortField = (sortBy == null || sortBy.isBlank()) ? "docDate" : sortBy;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        Page<Purchase> result = purchaseRepository.findHutangRows(
                start, end, scopeFilter, categories, search, searchColumn, isPaid, pageable);

        BigDecimal totalGrand = purchaseRepository.sumHutangGrandTotal(
                start, end, scopeFilter, categories, search, searchColumn, isPaid);

        return PurchaseSummaryResponse.<Purchase>builder()
                .content(result.getContent())
                .totalGrandSum(totalGrand)
                .totalQty(BigDecimal.ZERO)
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .build();
    }

    /** Semua baris hutang utk export Excel. */
    public List<Purchase> getAllHutangRows(
            String username, boolean isPaid,
            String startDateStr, String endDateStr, List<String> empCodes,
            List<String> categories, String search, String searchColumn) {

        User aktor = userAuthorizationService.requireUser(username);
        if (!userAuthorizationService.hasPermission(aktor, Permission.PEMBELIAN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses HUTANG");
        }
        List<String> scopeFilter = userAuthorizationService.effectiveScopeFilter(aktor, empCodes);
        if (scopeFilter != null && scopeFilter.isEmpty()) {
            return List.of();
        }

        LocalDate start = (startDateStr != null && !startDateStr.isBlank())
                ? LocalDate.parse(startDateStr)
                : LocalDate.of(2016, 1, 1);
        LocalDate end = (endDateStr != null && !endDateStr.isBlank())
                ? LocalDate.parse(endDateStr)
                : LocalDate.now();

        return purchaseRepository.findAllHutangRows(
                start, end, scopeFilter, categories, search, searchColumn, isPaid);
    }

    /** Daftar kode karyawan (dari tabel purchases) utk dropdown filter Hutang. */
    public java.util.List<com.stok.anandam.store.dto.EmployeeOption> getEmployeeOptions(String username) {
        User aktor = (username != null && !username.isBlank()) ? userAuthorizationService.requireUser(username) : null;
        List<String> allowed = aktor != null ? userAuthorizationService.allowedEmpCodes(aktor) : null;

        return purchaseRepository.findDistinctEmpCodeAndName().stream()
                .filter(obj -> {
                    if (allowed == null) return true;
                    String code = (String) obj[0];
                    return code != null && allowed.stream().anyMatch(a -> a.equalsIgnoreCase(code));
                })
                .map(obj -> com.stok.anandam.store.dto.EmployeeOption.builder()
                        .empCode((String) obj[0])
                        .empName((String) obj[1])
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    private PurchaseSummaryResponse<Purchase> emptySummary() {
        return PurchaseSummaryResponse.<Purchase>builder()
                .content(List.of())
                .totalGrandSum(BigDecimal.ZERO)
                .totalQty(BigDecimal.ZERO)
                .totalPages(0)
                .totalElements(0)
                .build();
    }

    public java.util.List<String> getCategories() {
        return purchaseRepository.findDistinctDepCodes();
    }
}
