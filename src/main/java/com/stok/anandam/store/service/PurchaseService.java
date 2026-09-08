package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.Purchase;
import com.stok.anandam.store.core.postgres.repository.PurchaseRepository;
import com.stok.anandam.store.dto.PurchaseSummaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PurchaseService {

        @Autowired
        private PurchaseRepository purchaseRepository;

        @Autowired
        private UserAuthorizationService userAuthorizationService;

        /** Backward-compatible entry: no username -> no masking. */
        public PurchaseSummaryResponse<Purchase> getPurchases(
                        int page, int size, String sortBy, String dir,
                        String startDateStr, String endDateStr, List<String> empCodes,
                        List<String> categories, String search, String searchColumn) {
                return getPurchases(page, size, sortBy, dir, startDateStr, endDateStr, empCodes, categories, search, searchColumn, null);
        }

        /**
         * List pembelian dengan pengawasan otorisasi:
         * jika user TIDAK punya permission PAYMENT_STATUS -> paidTotal/statusBayar disamarkan (null).
         *
         * @param username username user yang login (nullable utk kompatibilitas internal).
         */
        public PurchaseSummaryResponse<Purchase> getPurchases(
                        int page, int size, String sortBy, String dir,
                        String startDateStr, String endDateStr, List<String> empCodes,
                        List<String> categories, String search, String searchColumn, String username) {
                LocalDate start = (startDateStr != null && !startDateStr.isBlank())
                                ? LocalDate.parse(startDateStr)
                                : LocalDate.of(2016, 1, 1);

                LocalDate end = (endDateStr != null && !endDateStr.isBlank())
                                ? LocalDate.parse(endDateStr)
                                : LocalDate.now();

                Sort.Direction direction = dir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
                Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

                Page<Purchase> pageResult = purchaseRepository.findByDateRangeAndSearch(start, end, empCodes, categories, search, searchColumn, pageable);

                BigDecimal totalSum = purchaseRepository.sumGrandTotalByDateRange(start, end, empCodes, categories, search, searchColumn);
                Long totalQty = purchaseRepository.sumQtyByDateRange(start, end, empCodes, categories, search, searchColumn);

                java.util.List<Purchase> content = new java.util.ArrayList<>(pageResult.getContent());
                if (username != null) {
                        com.stok.anandam.store.core.postgres.model.User aktor = userAuthorizationService.requireUser(username);
                        if (!userAuthorizationService.canViewPaymentStatus(aktor)) {
                                for (Purchase p : content) {
                                        p.setPaidTotal(null);
                                        p.setStatusBayar(null);
                                }
                        }
                }

                return PurchaseSummaryResponse.<Purchase>builder()
                                .content(content)
                                .totalGrandSum(totalSum)
                                .totalQty(totalQty != null ? BigDecimal.valueOf(totalQty) : BigDecimal.ZERO)
                                .totalPages(pageResult.getTotalPages())
                                .totalElements(pageResult.getTotalElements())
                                .build();
        }

        public java.util.List<Purchase> getAllPurchasesForExport(String startDateStr, String endDateStr,
                        List<String> empCodes, List<String> categories, String search, String searchColumn) {
                return getAllPurchasesForExport(startDateStr, endDateStr, empCodes, categories, search, searchColumn, null);
        }

        public java.util.List<Purchase> getAllPurchasesForExport(String startDateStr, String endDateStr,
                        List<String> empCodes, List<String> categories, String search, String searchColumn, String username) {
                LocalDate start = (startDateStr != null && !startDateStr.isBlank())
                                ? LocalDate.parse(startDateStr)
                                : LocalDate.of(2016, 1, 1);

                LocalDate end = (endDateStr != null && !endDateStr.isBlank())
                                ? LocalDate.parse(endDateStr)
                                : LocalDate.now();

                return purchaseRepository.findAllByDateRangeAndSearch(start, end, empCodes, categories, search, searchColumn);
        }

        public java.util.List<String> getDeptCodes() {
                return purchaseRepository.findDistinctDepCodes();
        }
}