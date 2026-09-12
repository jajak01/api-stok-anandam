package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.Permission;
import com.stok.anandam.store.core.postgres.model.Sales;
import com.stok.anandam.store.core.postgres.model.User;
import com.stok.anandam.store.core.postgres.repository.SalesRepository;
import com.stok.anandam.store.dto.SalesSummaryResponse;
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
 * PIUTANG = data penjualan (kita menjual produk ke customer) yang belum/sudah
 * dibayar. Daftar tampil row-level seperti halaman Penjualan, dengan tab
 * PAID/UNPAID + filter tanggal/empCode/kategori/search (semua diteruskan ke
 * repository sehingga pemfilteran & paging terjadi di backend).
 */
@Service
public class PiutangService {

    @Autowired
    private SalesRepository salesRepository;

    @Autowired
    private UserAuthorizationService userAuthorizationService;

    public SalesSummaryResponse<Sales> getPiutang(
            String username, boolean isPaid, int page, int size, String sortBy, String dir,
            String startDateStr, String endDateStr, List<String> empCodes,
            List<String> categories, String search, String searchColumn) {

        User aktor = userAuthorizationService.requireUser(username);
        if (!userAuthorizationService.hasPermission(aktor, Permission.PIUTANG)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses PIUTANG");
        }

        List<String> scopeFilter = userAuthorizationService.effectiveScopeFilter(aktor, empCodes);
        if (scopeFilter != null && scopeFilter.isEmpty()) {
            return emptySummary();
        }

        LocalDate start = (startDateStr != null && !startDateStr.isBlank())
                ? LocalDate.parse(startDateStr)
                : LocalDate.of(2000, 1, 1);
        LocalDate end = (endDateStr != null && !endDateStr.isBlank())
                ? LocalDate.parse(endDateStr)
                : LocalDate.now();

        Sort.Direction direction = dir != null && dir.equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = (sortBy == null || sortBy.isBlank()) ? "docDate" : sortBy;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        Page<Sales> result = salesRepository.findPiutangRows(
                start, end, scopeFilter, categories, search, searchColumn, isPaid, pageable);

        BigDecimal totalGrand = salesRepository.sumPiutangGrandTotal(
                start, end, scopeFilter, categories, search, searchColumn, isPaid);

        return SalesSummaryResponse.<Sales>builder()
                .content(result.getContent())
                .totalGrandSum(totalGrand)
                .totalQty(BigDecimal.ZERO)
                .totalHpp(BigDecimal.ZERO)
                .totalLabaKotor(BigDecimal.ZERO)
                .marginPct(BigDecimal.ZERO)
                .totalPages(result.getTotalPages())
                .totalElements(result.getTotalElements())
                .build();
    }

    /** Semua baris piutang utk keperluan export Excel (tab/status yang sama). */
    public List<Sales> getAllPiutangRows(
            String username, boolean isPaid,
            String startDateStr, String endDateStr, List<String> empCodes,
            List<String> categories, String search, String searchColumn) {

        User aktor = userAuthorizationService.requireUser(username);
        if (!userAuthorizationService.hasPermission(aktor, Permission.PIUTANG)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses PIUTANG");
        }

        List<String> scopeFilter = userAuthorizationService.effectiveScopeFilter(aktor, empCodes);
        if (scopeFilter != null && scopeFilter.isEmpty()) {
            return List.of();
        }

        LocalDate start = (startDateStr != null && !startDateStr.isBlank())
                ? LocalDate.parse(startDateStr)
                : LocalDate.of(2000, 1, 1);
        LocalDate end = (endDateStr != null && !endDateStr.isBlank())
                ? LocalDate.parse(endDateStr)
                : LocalDate.now();

        return salesRepository.findAllPiutangRows(
                start, end, scopeFilter, categories, search, searchColumn, isPaid);
    }

    /** Daftar kode karyawan (dari tabel sales) utk dropdown filter Piutang. */
    public java.util.List<com.stok.anandam.store.dto.EmployeeOption> getEmployeeOptions(String username) {
        User aktor = (username != null && !username.isBlank()) ? userAuthorizationService.requireUser(username) : null;
        List<String> allowed = aktor != null ? userAuthorizationService.allowedEmpCodes(aktor) : null;

        return salesRepository.findDistinctEmpCodeAndName().stream()
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

    public java.util.List<String> getCategories() {
        return salesRepository.findDistinctDepCodes();
    }

    private SalesSummaryResponse<Sales> emptySummary() {
        return SalesSummaryResponse.<Sales>builder()
                .content(List.of())
                .totalGrandSum(BigDecimal.ZERO)
                .totalQty(BigDecimal.ZERO)
                .totalHpp(BigDecimal.ZERO)
                .totalLabaKotor(BigDecimal.ZERO)
                .marginPct(BigDecimal.ZERO)
                .totalPages(0)
                .totalElements(0)
                .build();
    }
}
