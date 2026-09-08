package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.Sales;
import com.stok.anandam.store.core.postgres.repository.SalesRepository;
import com.stok.anandam.store.dto.MarketingItemDetailResponse;
import com.stok.anandam.store.dto.MarketingItemRow;
import com.stok.anandam.store.dto.MarketingNotaDetailResponse;
import com.stok.anandam.store.dto.MarketingNotaRow;
import com.stok.anandam.store.dto.MarketingSalesRow;
import com.stok.anandam.store.dto.MarketingSalesSummaryResponse;
import com.stok.anandam.store.dto.MarketingTimelinePoint;
import com.stok.anandam.store.dto.MarketingTimelineResponse;
import com.stok.anandam.store.dto.ProfitabilityResponse;
import com.stok.anandam.store.dto.ProfitabilityRowResponse;
import com.stok.anandam.store.dto.SalesSummaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SalesService {

        @Autowired
        private SalesRepository salesRepository;

        @Autowired
        private UserAuthorizationService userAuthorizationService;

        /** Backward-compatible entry: no username -> full visibility, no masking. */
        public SalesSummaryResponse<Sales> getSales(
                        int page, int size, String sortBy, String dir,
                        String startDateStr, String endDateStr, List<String> empCodes,
                        List<String> categories, String search, String searchColumn) {
                return getSales(page, size, sortBy, dir, startDateStr, endDateStr, empCodes, categories, search, searchColumn, null);
        }

        /**
         * List penjualan dengan pengawasan otorisasi:
         * 1. Jika user punya empCodeAccess non-kosong -> scope dibatasi ke emp codes tsb (+ employeeCode sendiri).
         * 2. Jika user TIDAK punya permission PAYMENT_STATUS -> paidTotal/statusBayar disamarkan (null).
         *
         * @param username username user yang login (nullable utk kompatibilitas internal).
         */
        public SalesSummaryResponse<Sales> getSales(
                        int page, int size, String sortBy, String dir,
                        String startDateStr, String endDateStr, List<String> empCodes,
                        List<String> categories, String search, String searchColumn, String username) {
                // 1. Parsing Tanggal
                LocalDate start = (startDateStr != null && !startDateStr.isBlank())
                                ? LocalDate.parse(startDateStr)
                                : LocalDate.of(2000, 1, 1);

                LocalDate end = (endDateStr != null && !endDateStr.isBlank())
                                ? LocalDate.parse(endDateStr)
                                : LocalDate.now();

                com.stok.anandam.store.core.postgres.model.User aktor = username != null ? userAuthorizationService.requireUser(username) : null;
                List<String> scopeFilter = userAuthorizationService.effectiveScopeFilter(aktor, empCodes);
                boolean maskPayment = aktor != null && !userAuthorizationService.canViewPaymentStatus(aktor);

                // Scope kosong -> tidak ada data yang boleh terlihat (hindari IN () kosong).
                if (scopeFilter != null && scopeFilter.isEmpty()) {
                        return emptySummary();
                }

                // 2. Setup Paging
                Sort.Direction direction = dir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
                Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

                // 3. Ambil Data
                Page<Sales> pageResult = salesRepository.findByFilters(start, end, scopeFilter, categories, search, searchColumn, pageable);

                // 4. Ambil Total Sum
                BigDecimal totalSum = salesRepository.sumGrandTotalByFilters(start, end, scopeFilter, categories, search, searchColumn);
                Long totalQty = salesRepository.sumQtyByFilters(start, end, scopeFilter, categories, search, searchColumn);
                BigDecimal totalHpp = salesRepository.sumTotalHppByFilters(start, end, scopeFilter, categories, search, searchColumn);
                BigDecimal totalLaba = salesRepository.sumLabaKotorByFilters(start, end, scopeFilter, categories, search, searchColumn);

                List<Sales> content = new java.util.ArrayList<>(pageResult.getContent());
                if (maskPayment) {
                        for (Sales s : content) {
                                s.setPaidTotal(null);
                                s.setStatusBayar(null);
                        }
                }

                // 5. Return Response
                return SalesSummaryResponse.<Sales>builder()
                                .content(content)
                                .totalGrandSum(totalSum)
                                .totalQty(totalQty != null ? BigDecimal.valueOf(totalQty) : BigDecimal.ZERO)
                                .totalHpp(totalHpp != null ? totalHpp : BigDecimal.ZERO)
                                .totalLabaKotor(totalLaba != null ? totalLaba : BigDecimal.ZERO)
                                .marginPct(marginPct(totalSum, totalLaba))
                                .totalPages(pageResult.getTotalPages())
                                .totalElements(pageResult.getTotalElements())
                                .build();
        }

        public java.util.List<Sales> getAllSalesForExport(String startDateStr, String endDateStr, List<String> empCodes,
                        List<String> categories, String search, String searchColumn) {
                return getAllSalesForExport(startDateStr, endDateStr, empCodes, categories, search, searchColumn, null);
        }

        /** Export juga tunduk pada scope EMP (backend enforcement). */
        public java.util.List<Sales> getAllSalesForExport(String startDateStr, String endDateStr, List<String> empCodes,
                        List<String> categories, String search, String searchColumn, String username) {
                LocalDate start = (startDateStr != null && !startDateStr.isBlank())
                                ? LocalDate.parse(startDateStr)
                                : LocalDate.of(2000, 1, 1);

                LocalDate end = (endDateStr != null && !endDateStr.isBlank())
                                ? LocalDate.parse(endDateStr)
                                : LocalDate.now();

                com.stok.anandam.store.core.postgres.model.User aktor = username != null ? userAuthorizationService.requireUser(username) : null;
                List<String> scopeFilter = userAuthorizationService.effectiveScopeFilter(aktor, empCodes);
                if (scopeFilter != null && scopeFilter.isEmpty()) {
                        return new java.util.ArrayList<>();
                }

                return salesRepository.findAllByFilters(start, end, scopeFilter, categories, search, searchColumn);
        }

        private SalesSummaryResponse<Sales> emptySummary() {
                return SalesSummaryResponse.<Sales>builder()
                                .content(new java.util.ArrayList<>())
                                .totalGrandSum(BigDecimal.ZERO)
                                .totalQty(BigDecimal.ZERO)
                                .totalHpp(BigDecimal.ZERO)
                                .totalLabaKotor(BigDecimal.ZERO)
                                .marginPct(BigDecimal.ZERO)
                                .totalPages(0)
                                .totalElements(0)
                                .build();
        }

        private ProfitabilityResponse<ProfitabilityRowResponse> emptyProfitability() {
                return ProfitabilityResponse.<ProfitabilityRowResponse>builder()
                                .content(new java.util.ArrayList<>())
                                .totalQty(BigDecimal.ZERO)
                                .totalOmset(BigDecimal.ZERO)
                                .totalHpp(BigDecimal.ZERO)
                                .totalLabaKotor(BigDecimal.ZERO)
                                .marginPct(BigDecimal.ZERO)
                                .build();
        }

        public java.util.List<com.stok.anandam.store.dto.EmployeeOption> getEmployeeCodes() {
                return salesRepository.findDistinctEmpCodeAndName().stream()
                                .map(obj -> com.stok.anandam.store.dto.EmployeeOption.builder()
                                                .empCode((String) obj[0])
                                                .empName((String) obj[1])
                                                .build())
                                .collect(java.util.stream.Collectors.toList());
        }

        public java.util.List<String> getDeptCodes() {
                return salesRepository.findDistinctDepCodes();
        }

        /**
         * Laporan Profitabilitas Penjualan per barang.
         * Source: tabel sales (Postgres) yang sudah diisi kolom hpp_satuan/total_hpp/laba_kotor
         * oleh service migrasi. Retur (RJ) sudah diperhitungkan di level baris.
         */
        /** Backward-compatible entry: no username -> full visibility. */
        public ProfitabilityResponse<ProfitabilityRowResponse> getProfitability(
                        String startDateStr, String endDateStr, List<String> empCodes,
                        List<String> categories, String search, String searchColumn) {
                return getProfitability(startDateStr, endDateStr, empCodes, categories, search, searchColumn, null);
        }

        /** Profitabilitas juga tunduk pada scope EMP (backend enforcement). */
        public ProfitabilityResponse<ProfitabilityRowResponse> getProfitability(
                        String startDateStr, String endDateStr, List<String> empCodes,
                        List<String> categories, String search, String searchColumn, String username) {

                LocalDate start = (startDateStr != null && !startDateStr.isBlank())
                                ? LocalDate.parse(startDateStr)
                                : LocalDate.of(2000, 1, 1);

                LocalDate end = (endDateStr != null && !endDateStr.isBlank())
                                ? LocalDate.parse(endDateStr)
                                : LocalDate.now();

                com.stok.anandam.store.core.postgres.model.User aktor = username != null ? userAuthorizationService.requireUser(username) : null;
                List<String> scopeFilter = userAuthorizationService.effectiveScopeFilter(aktor, empCodes);
                if (scopeFilter != null && scopeFilter.isEmpty()) {
                        return emptyProfitability();
                }

                List<Object[]> rows = salesRepository.findProfitabilityByFilters(start, end, scopeFilter, categories, search, searchColumn);

                List<ProfitabilityRowResponse> content = rows.stream().map(row -> {
                        BigDecimal omset = num((Object) row[5]);
                        BigDecimal totalHpp = num((Object) row[6]);
                        BigDecimal labaKotor = num((Object) row[7]);

                        return ProfitabilityRowResponse.builder()
                                        .itemCode((String) row[0])
                                        .itemName((String) row[1])
                                        .depCode((String) row[2])
                                        .depName((String) row[3])
                                        .qty(num((Object) row[4]))
                                        .omset(omset)
                                        .totalHpp(totalHpp)
                                        .labaKotor(labaKotor)
                                        .marginPct(marginPct(omset, labaKotor))
                                        .build();
                }).collect(Collectors.toList());

                // Total footer dihitung langsung dari baris hasil (tidak lewat query agregat
                // tambahan, karena proyeksi Object[] agregat tanpa GROUP BY di Hibernate
                // bisa dikembalikan sebagai array dengan panjang tidak terduga).
                BigDecimal totalQty = BigDecimal.ZERO;
                BigDecimal totalOmset = BigDecimal.ZERO;
                BigDecimal totalHpp = BigDecimal.ZERO;
                BigDecimal totalLaba = BigDecimal.ZERO;
                for (ProfitabilityRowResponse r : content) {
                        totalQty = totalQty.add(val(r.getQty()));
                        totalOmset = totalOmset.add(val(r.getOmset()));
                        totalHpp = totalHpp.add(val(r.getTotalHpp()));
                        totalLaba = totalLaba.add(val(r.getLabaKotor()));
                }

                return ProfitabilityResponse.<ProfitabilityRowResponse>builder()
                                .content(content)
                                .totalQty(totalQty)
                                .totalOmset(totalOmset)
                                .totalHpp(totalHpp)
                                .totalLabaKotor(totalLaba)
                                .marginPct(marginPct(totalOmset, totalLaba))
                                .build();
        }

        /** Konversi nilai SUM (Long/BigDecimal/null) menjadi BigDecimal aman. */
        private BigDecimal num(Object o) {
                if (o == null)
                        return BigDecimal.ZERO;
                if (o instanceof BigDecimal bd)
                        return bd;
                if (o instanceof Number n)
                        return BigDecimal.valueOf(n.doubleValue());
                return BigDecimal.ZERO;
        }

        /** BigDecimal non-null helper untuk penjumlahan. */
        private BigDecimal val(BigDecimal v) {
                return v == null ? BigDecimal.ZERO : v;
        }

        /** margin % = labaKotor / omset * 100 (2 desimal, hindari pembagian nol). */
        private BigDecimal marginPct(BigDecimal omset, BigDecimal laba) {
                if (omset == null || omset.compareTo(BigDecimal.ZERO) == 0)
                        return BigDecimal.ZERO;
                return laba.multiply(BigDecimal.valueOf(100))
                                .divide(omset, 2, RoundingMode.HALF_UP);
        }
// ==================== LAPORAN OMSET & MARGIN PER MARKETING ====================

        /**
         * Ringkasan omzet + margin kotor per marketing untuk satu periode.
         *
         * @param period   DAY | WEEK | MONTH | YEAR
         * @param date     tanggal acuan (YYYY-MM-DD) untuk menentukan rentang periode
         * @param empCodes daftar kode marketing (opsional). Jika null/empty -> semua marketing.
         */
        public MarketingSalesSummaryResponse getMarketingSalesSummary(String period, LocalDate date, List<String> empCodes) {
                return getMarketingSalesSummary(period, date, null, null, empCodes);
        }

        /** Overload dgn rentang eksplisit (lightweight): jika start/end diberikan, dipakai langsung. */
        public MarketingSalesSummaryResponse getMarketingSalesSummary(String period, LocalDate date, LocalDate startDate, LocalDate endDate, List<String> empCodes) {
                PeriodRange range = resolveRange(period, date, startDate, endDate);
                List<String> filter = normalizeEmpCodes(empCodes);

                List<MarketingSalesRow> content = salesRepository
                                .sumMarketingSalesByFilters(range.start, range.end, filter).stream()
                                .map(row -> {
                                        BigDecimal omset = num(row[3]);
                                        BigDecimal laba = num(row[5]);
                                        return MarketingSalesRow.builder()
                                                        .empCode(((Object) row[0]) == null ? null : (String) row[0])
                                                        .empName(((Object) row[1]) == null ? null : (String) row[1])
                                                        .qty(num(row[2]))
                                                        .omset(omset)
                                                        .totalHpp(num(row[4]))
                                                        .labaKotor(laba)
                                                        .marginPct(marginPct(omset, laba))
                                                        .build();
                                })
                                .collect(Collectors.toList());

                BigDecimal totalOmset = BigDecimal.ZERO;
                BigDecimal totalHpp = BigDecimal.ZERO;
                BigDecimal totalLaba = BigDecimal.ZERO;
                BigDecimal totalQty = BigDecimal.ZERO;
                for (MarketingSalesRow r : content) {
                        totalOmset = totalOmset.add(val(r.getOmset()));
                        totalHpp = totalHpp.add(val(r.getTotalHpp()));
                        totalLaba = totalLaba.add(val(r.getLabaKotor()));
                        totalQty = totalQty.add(val(r.getQty()));
                }

                return MarketingSalesSummaryResponse.builder()
                                .period(period.toUpperCase())
                                .range(MarketingSalesSummaryResponse.LocalDateRange.builder()
                                                .start(range.start).end(range.end).build())
                                .content(content)
                                .totalQty(totalQty)
                                .totalOmset(totalOmset)
                                .totalHpp(totalHpp)
                                .totalLabaKotor(totalLaba)
                                .marginPct(marginPct(totalOmset, totalLaba))
                                .build();
        }

        /**
         * Menentukan rentang tanggal untuk filter periode (DAY/WEEK/MONTH/YEAR).
         * WEEK dimulai Senin. Range inklusif (start..end).
         */
        private PeriodRange resolveRange(String period, LocalDate date) {
                return resolveRange(period, date, null, null);
        }

        /**
         * Menentukan rentang tanggal untuk filter periode (DAY/WEEK/MONTH/YEAR).
         * Jika startDate &amp; endDate diberikan (lightweight/range penuh), dipakai langsung;
         * jika tidak, dihitung dari period + date (WEEK mulai Senin, range inklusif).
         */
        private PeriodRange resolveRange(String period, LocalDate date, LocalDate startDate, LocalDate endDate) {
                if (startDate != null && endDate != null) {
                        return new PeriodRange(startDate, endDate);
                }
                LocalDate ref = date != null ? date : LocalDate.now();
                String p = period == null ? "" : period.toUpperCase();

                if ("WEEK".equals(p)) {
                        LocalDate start = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                        return new PeriodRange(start, start.plusDays(6));
                } else if ("MONTH".equals(p)) {
                        LocalDate start = ref.withDayOfMonth(1);
                        return new PeriodRange(start, ref.with(TemporalAdjusters.lastDayOfMonth()));
                } else if ("YEAR".equals(p)) {
                        LocalDate start = ref.withDayOfYear(1);
                        return new PeriodRange(start, ref.with(TemporalAdjusters.lastDayOfYear()));
                } else { // DAY (default)
                        return new PeriodRange(ref, ref);
                }
        }
        /**
         * Detail drill-down per nota (doc) untuk satu marketing pada periode.
         *
         * @param period  DAY | WEEK | MONTH | YEAR
         * @param date    tanggal acuan (YYYY-MM-DD)
         * @param empCode kode marketing. Jika null -> semua marketing (ringkasan nota).
         */
        public MarketingNotaDetailResponse getMarketingSalesNotaDetail(String period, LocalDate date, String empCode) {
                return getMarketingSalesNotaDetail(period, date, null, null, empCode);
        }

        /** Overload dgn rentang eksplisit (lightweight). */
        public MarketingNotaDetailResponse getMarketingSalesNotaDetail(String period, LocalDate date, LocalDate startDate, LocalDate endDate, String empCode) {
                PeriodRange range = resolveRange(period, date, startDate, endDate);
                List<String> filter = normalizeEmpCodes(empCode == null ? null : Collections.singletonList(empCode));

                List<MarketingNotaRow> content = salesRepository
                                .findNotaByFilters(range.start, range.end, filter).stream()
                                .map(row -> {
                                        BigDecimal omset = num(row[5]);
                                        BigDecimal laba = num(row[7]);
                                        return MarketingNotaRow.builder()
                                                        .docNo(((Object) row[0]) == null ? null : (String) row[0])
                                                        .docDate(toLocalDate(row[1]))
                                                        .code(((Object) row[2]) == null ? null : (String) row[2])
                                                        .parName(((Object) row[3]) == null ? null : (String) row[3])
                                                        .qty(num(row[4]))
                                                        .omset(omset)
                                                        .totalHpp(num(row[6]))
                                                        .labaKotor(laba)
                                                        .marginPct(marginPct(omset, laba))
                                                        .empCode(((Object) row[8]) == null ? null : (String) row[8])
                                                        .empName(((Object) row[9]) == null ? null : (String) row[9])
                                                        .build();
                                })
                                .collect(Collectors.toList());

                BigDecimal totalOmset = BigDecimal.ZERO;
                BigDecimal totalHpp = BigDecimal.ZERO;
                BigDecimal totalLaba = BigDecimal.ZERO;
                BigDecimal totalQty = BigDecimal.ZERO;
                for (MarketingNotaRow r : content) {
                        totalOmset = totalOmset.add(val(r.getOmset()));
                        totalHpp = totalHpp.add(val(r.getTotalHpp()));
                        totalLaba = totalLaba.add(val(r.getLabaKotor()));
                        totalQty = totalQty.add(val(r.getQty()));
                }

                String resolvedName = (empCode != null && !empCode.isBlank() && !content.isEmpty())
                                ? content.get(0).getEmpName() : null;

                return MarketingNotaDetailResponse.builder()
                                .empCode(empCode)
                                .empName(resolvedName)
                                .period(period.toUpperCase())
                                .start(range.start)
                                .end(range.end)
                                .content(content)
                                .totalQty(totalQty)
                                .totalOmset(totalOmset)
                                .totalHpp(totalHpp)
                                .totalLabaKotor(totalLaba)
                                .marginPct(marginPct(totalOmset, totalLaba))
                                .build();
        }
/**
         * Detail drill-down per barang (item) untuk satu marketing pada periode.
         *
         * @param period  DAY | WEEK | MONTH | YEAR
         * @param date    tanggal acuan (YYYY-MM-DD)
         * @param empCode kode marketing. Jika null -> semua marketing (ringkasan barang).
         */
        public MarketingItemDetailResponse getMarketingSalesItemDetail(String period, LocalDate date, String empCode) {
                return getMarketingSalesItemDetail(period, date, null, null, empCode);
        }

        /** Overload dgn rentang eksplisit (lightweight). */
        public MarketingItemDetailResponse getMarketingSalesItemDetail(String period, LocalDate date, LocalDate startDate, LocalDate endDate, String empCode) {
                PeriodRange range = resolveRange(period, date, startDate, endDate);
                List<String> filter = normalizeEmpCodes(empCode == null ? null : Collections.singletonList(empCode));

                List<MarketingItemRow> content = salesRepository
                                .findItemByFilters(range.start, range.end, filter).stream()
                                .map(row -> {
                                        BigDecimal omset = num(row[5]);
                                        BigDecimal laba = num(row[7]);
                                        return MarketingItemRow.builder()
                                                        .iteCode(((Object) row[0]) == null ? null : (String) row[0])
                                                        .itemName(((Object) row[1]) == null ? null : (String) row[1])
                                                        .depCode(((Object) row[2]) == null ? null : (String) row[2])
                                                        .depName(((Object) row[3]) == null ? null : (String) row[3])
                                                        .qty(num(row[4]))
                                                        .omset(omset)
                                                        .totalHpp(num(row[6]))
                                                        .labaKotor(laba)
                                                        .marginPct(marginPct(omset, laba))
                                                        .empCode(((Object) row[8]) == null ? null : (String) row[8])
                                                        .empName(((Object) row[9]) == null ? null : (String) row[9])
                                                        .build();
                                })
                                .collect(Collectors.toList());

                BigDecimal totalOmset = BigDecimal.ZERO;
                BigDecimal totalHpp = BigDecimal.ZERO;
                BigDecimal totalLaba = BigDecimal.ZERO;
                BigDecimal totalQty = BigDecimal.ZERO;
                for (MarketingItemRow r : content) {
                        totalOmset = totalOmset.add(val(r.getOmset()));
                        totalHpp = totalHpp.add(val(r.getTotalHpp()));
                        totalLaba = totalLaba.add(val(r.getLabaKotor()));
                        totalQty = totalQty.add(val(r.getQty()));
                }

                String resolvedName = (empCode != null && !empCode.isBlank() && !content.isEmpty())
                                ? content.get(0).getEmpName() : null;

                return MarketingItemDetailResponse.builder()
                                .empCode(empCode)
                                .empName(resolvedName)
                                .period(period.toUpperCase())
                                .start(range.start)
                                .end(range.end)
                                .content(content)
                                .totalQty(totalQty)
                                .totalOmset(totalOmset)
                                .totalHpp(totalHpp)
                                .totalLabaKotor(totalLaba)
                                .marginPct(marginPct(totalOmset, totalLaba))
                                .build();
        }

        public MarketingTimelineResponse getMarketingSalesTimeline(
                        String period, LocalDate date, LocalDate startDate, LocalDate endDate, List<String> empCodes) {
                PeriodRange range = resolveRange(period, date, startDate, endDate);
                List<String> filter = normalizeEmpCodes(empCodes);

                String p = period == null ? "DAY" : period.toUpperCase();
                List<Object[]> rawList;
                if ("YEAR".equals(p)) {
                        rawList = salesRepository.sumTimelineYear(range.start, range.end, filter);
                } else if ("MONTH".equals(p)) {
                        rawList = salesRepository.sumTimelineMonth(range.start, range.end, filter);
                } else {
                        rawList = salesRepository.sumTimelineDay(range.start, range.end, filter);
                }

                List<MarketingTimelinePoint> points = new java.util.ArrayList<>();
                BigDecimal totalOmset = BigDecimal.ZERO;
                BigDecimal totalHpp = BigDecimal.ZERO;
                BigDecimal totalLaba = BigDecimal.ZERO;

                for (Object[] row : rawList) {
                        String key = row[0] == null ? "" : row[0].toString();
                        BigDecimal omset = num(row[1]);
                        BigDecimal hpp = num(row[2]);
                        BigDecimal laba = num(row[3]);
                        Double margin = omset.compareTo(BigDecimal.ZERO) > 0
                                        ? laba.divide(omset, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                                        : 0.0;

                        points.add(MarketingTimelinePoint.builder()
                                        .key(key)
                                        .label(formatTimelineLabel(key, p))
                                        .omset(omset)
                                        .totalHpp(hpp)
                                        .labaKotor(laba)
                                        .marginPct(margin)
                                        .build());

                        totalOmset = totalOmset.add(omset);
                        totalHpp = totalHpp.add(hpp);
                        totalLaba = totalLaba.add(laba);
                }

                Double totalMargin = totalOmset.compareTo(BigDecimal.ZERO) > 0
                                ? totalLaba.divide(totalOmset, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                                : 0.0;

                return MarketingTimelineResponse.builder()
                                .period(p)
                                .start(range.start)
                                .end(range.end)
                                .empCode(empCodes != null && empCodes.size() == 1 ? empCodes.get(0) : null)
                                .points(points)
                                .totalOmset(totalOmset)
                                .totalHpp(totalHpp)
                                .totalLabaKotor(totalLaba)
                                .marginPct(totalMargin)
                                .build();
        }

        private String formatTimelineLabel(String key, String period) {
                if (key == null || key.isBlank()) return "";
                if ("YEAR".equals(period)) return key;
                if ("MONTH".equals(period)) {
                        try {
                                if (key.length() >= 7) {
                                        int m = Integer.parseInt(key.substring(5, 7));
                                        String[] months = {"", "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"};
                                        if (m >= 1 && m <= 12) return months[m];
                                }
                        } catch (Exception ignored) {}
                        return key;
                }
                if (key.length() >= 10) {
                        return key.substring(8, 10) + "/" + key.substring(5, 7);
                }
                return key;
        }

        private LocalDate toLocalDate(Object o) {
                if (o == null) return null;
                if (o instanceof LocalDate ld) return ld;
                if (o instanceof java.sql.Date sd) return sd.toLocalDate();
                if (o instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
                if (o instanceof java.util.Date ud) return new java.sql.Date(ud.getTime()).toLocalDate();
                if (o instanceof String s && !s.isBlank()) {
                        try {
                                return LocalDate.parse(s);
                        } catch (Exception ignored) {}
                }
                return null;
        }

        /** Normalisasi list kode marketing: blank/null menjadi null (semua), else list non-empty. */
        private List<String> normalizeEmpCodes(List<String> empCodes) {
                if (empCodes == null || empCodes.isEmpty())
                        return null;
                List<String> cleaned = new java.util.ArrayList<>();
                for (String e : empCodes) {
                        if (e != null && !e.trim().isEmpty()) {
                                String upper = e.trim().toUpperCase();
                                String underscored = upper.replace(" ", "_");
                                if (!cleaned.contains(underscored)) cleaned.add(underscored);
                                if (!cleaned.contains(upper)) cleaned.add(upper);
                        }
                }
                return cleaned.isEmpty() ? null : cleaned;
        }

        /** Nilai rentang tanggal (start & end) untuk filter periode. */
        private static class PeriodRange {
                final LocalDate start;
                final LocalDate end;

                PeriodRange(LocalDate start, LocalDate end) {
                        this.start = start;
                        this.end = end;
                }
        }
}