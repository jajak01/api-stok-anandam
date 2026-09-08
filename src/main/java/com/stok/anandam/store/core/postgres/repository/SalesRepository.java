package com.stok.anandam.store.core.postgres.repository;

import com.stok.anandam.store.core.postgres.model.Sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SalesRepository extends JpaRepository<Sales, Long> {

        @Modifying
        @Transactional
        @Query(value = "TRUNCATE TABLE sales RESTART IDENTITY", nativeQuery = true)
        void truncateTable();

        // QUERY 1: Ambil Data List dengan Filter Lengkap
        @Query("SELECT s FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR s.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR s.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'code' AND LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ")")
        Page<Sales> findByFilters(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn,
                        Pageable pageable);

        @Query("SELECT s FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR s.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR s.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'code' AND LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ") ORDER BY s.docDate DESC")
        List<Sales> findAllByFilters(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn);

        // QUERY 2: Hitung Total Grand (SUM) dengan filter yang sama
        @Query("SELECT COALESCE(SUM(s.grandTotal), 0) FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR s.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR s.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'code' AND LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ")")
        BigDecimal sumGrandTotalByFilters(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn);

        @Query("SELECT COALESCE(SUM(s.grandTotal), 0) FROM Sales s WHERE s.docDate = :today")
        BigDecimal sumTotalByDate(@Param("today") LocalDate today);

        @Query("SELECT COALESCE(SUM(s.qty), 0) FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR s.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR s.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'code' AND LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ")")
        Long sumQtyByFilters(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn);

        // QUERY: Total HPP (SUM) dengan filter yang sama
        @Query("SELECT COALESCE(SUM(s.totalHpp), 0) FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR s.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR s.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'code' AND LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ")")
        BigDecimal sumTotalHppByFilters(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn);

        // QUERY: Total Laba Kotor (SUM) dengan filter yang sama
        @Query("SELECT COALESCE(SUM(s.labaKotor), 0) FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR s.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR s.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'code' AND LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ")")
        BigDecimal sumLabaKotorByFilters(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn);

        @Query("SELECT MAX(s.docDate) FROM Sales s WHERE s.itemName = :itemName")
        LocalDate findLatestDocDateByItemName(@Param("itemName") String itemName);

        @Query("SELECT s.itemName, MAX(s.docDate) FROM Sales s WHERE s.itemName IN :itemNames GROUP BY s.itemName")
        List<Object[]> findLatestDocDatesByItemNames(@Param("itemNames") List<String> itemNames);

        @Query("SELECT s.empCode, MIN(s.empName), COALESCE(SUM(s.grandTotal), 0) FROM Sales s " +
                        "WHERE s.docDate = :today " +
                        "GROUP BY s.empCode " +
                        "ORDER BY SUM(s.grandTotal) DESC")
        List<Object[]> sumSalesByEmployeeToday(@Param("today") LocalDate today);

        @Query("SELECT s.empCode, MIN(s.empName), COALESCE(SUM(s.grandTotal), 0) FROM Sales s " +
                        "WHERE s.docDate BETWEEN :start AND :end " +
                        "GROUP BY s.empCode " +
                        "ORDER BY SUM(s.grandTotal) DESC")
        List<Object[]> sumSalesByEmployeeMonth(@Param("start") LocalDate start, @Param("end") LocalDate end);

        @Query("SELECT DISTINCT s.empCode, s.empName FROM Sales s WHERE s.empCode IS NOT NULL AND TRIM(s.empCode) <> '' ORDER BY s.empCode")
        java.util.List<Object[]> findDistinctEmpCodeAndName();

        @Query("SELECT DISTINCT s.empCode FROM Sales s WHERE s.empCode IS NOT NULL AND TRIM(s.empCode) <> '' ORDER BY s.empCode")
        java.util.List<String> findDistinctEmpCodeOrderByEmpCode();

        @Query("SELECT DISTINCT s.depCode FROM Sales s WHERE s.depCode IS NOT NULL AND TRIM(s.depCode) <> '' ORDER BY s.depCode")
        java.util.List<String> findDistinctDepCodes();

        // Auto-match JL: Cari Sales berdasarkan nama pelanggan (case-insensitive) + grandTotal (sum dari semua item), ambil yang terbaru
        // Menggunakan REPLACE(s.parName, ' ', '') untuk mengabaikan perbedaan spasi (spasi ganda, trailing/leading spaces)
        @Query("SELECT s.docNo FROM Sales s " +
                        "WHERE REPLACE(LOWER(TRIM(s.parName)), ' ', '') = REPLACE(LOWER(TRIM(:parName)), ' ', '') " +
                        "GROUP BY s.docNo " +
                        "HAVING SUM(s.grandTotal) = :grandTotal " +
                        "ORDER BY MAX(s.docDate) DESC")
        List<String> findDocNoByParNameIgnoreCaseAndGrandTotal(
                        @Param("parName") String parName,
                        @Param("grandTotal") BigDecimal grandTotal);

        @Query("SELECT s.docNo FROM Sales s " +
                        "WHERE REPLACE(LOWER(TRIM(s.parName)), ' ', '') = REPLACE(LOWER(TRIM(:parName)), ' ', '') " +
                        "AND s.docDate >= :minDate " +
                        "AND NOT EXISTS (SELECT 1 FROM Memo m WHERE m.nomorJl = s.docNo) " +
                        "GROUP BY s.docNo " +
                        "HAVING SUM(s.grandTotal) = :grandTotal " +
                        "ORDER BY MAX(s.docDate) DESC")
        List<String> findDocNoForAutoMatch(
                        @Param("parName") String parName,
                        @Param("grandTotal") BigDecimal grandTotal,
                        @Param("minDate") LocalDate minDate);

        // ==================== PROFITABILITAS PENJUALAN ====================
        // Ringkasan per barang: qty, omset (grand_total), total HPP, laba kotor.
        // Nilai retur (RJ) sudah diperhitungkan di level baris saat migrasi,
        // jadi agregasi di sini cukup menjumlahkan kolom yang sudah ada.
        @Query("SELECT s.iteCode, s.itemName, MAX(s.depCode), MAX(s.depName), " +
                        "COALESCE(SUM(s.qty), 0), COALESCE(SUM(s.grandTotal), 0), " +
                        "COALESCE(SUM(s.totalHpp), 0), COALESCE(SUM(s.labaKotor), 0) " +
                        "FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR s.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR s.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.iteCode) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'iteCode' AND LOWER(s.iteCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ") " +
                        "GROUP BY s.iteCode, s.itemName " +
                        "ORDER BY COALESCE(SUM(s.labaKotor), 0) DESC")
        java.util.List<Object[]> findProfitabilityByFilters(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") java.util.List<String> empCodes,
                        @Param("categories") java.util.List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn);

        // ==================== LAPORAN OMSET PER MARKETING ====================
        // Ringkasan per marketing: qty, omset (grand_total), total HPP, laba kotor.
        // Nilai retur (RJ) sudah diperhitungkan di level baris saat migrasi,
        // jadi agregasi cukup menjumlahkan kolom yang sudah ada.
        // empCodes nullable -> jika diisi hanya satu marketing (untuk drill-down),
        // jika null -> mencakup semua marketing (untuk ringkasan).
        @Query("SELECT UPPER(TRIM(s.empCode)), MIN(s.empName), " +
                        "COALESCE(SUM(s.qty), 0), COALESCE(SUM(s.grandTotal), 0), " +
                        "COALESCE(SUM(s.totalHpp), 0), COALESCE(SUM(s.labaKotor), 0) " +
                        "FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR UPPER(TRIM(s.empCode)) IN :empCodes) " +
                        "GROUP BY UPPER(TRIM(s.empCode)) " +
                        "ORDER BY COALESCE(SUM(s.grandTotal), 0) DESC")
        java.util.List<Object[]> sumMarketingSalesByFilters(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") java.util.List<String> empCodes);

        // Detail drill-down per nota (doc) untuk satu marketing pada periode.
        @Query("SELECT s.docNo, s.docDate, MAX(s.code), MAX(s.parName), " +
                        "COALESCE(SUM(s.qty), 0), COALESCE(SUM(s.grandTotal), 0), " +
                        "COALESCE(SUM(s.totalHpp), 0), COALESCE(SUM(s.labaKotor), 0), " +
                        "MAX(UPPER(TRIM(s.empCode))), MAX(s.empName) " +
                        "FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR UPPER(TRIM(s.empCode)) IN :empCodes) " +
                        "GROUP BY s.docNo, s.docDate " +
                        "ORDER BY s.docDate DESC")
        java.util.List<Object[]> findNotaByFilters(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") java.util.List<String> empCodes);

        // Detail drill-down per barang (item) untuk satu marketing pada periode.
        @Query("SELECT s.iteCode, MIN(s.itemName), MAX(s.depCode), MAX(s.depName), " +
                        "COALESCE(SUM(s.qty), 0), COALESCE(SUM(s.grandTotal), 0), " +
                        "COALESCE(SUM(s.totalHpp), 0), COALESCE(SUM(s.labaKotor), 0), " +
                        "MAX(UPPER(TRIM(s.empCode))), MAX(s.empName) " +
                        "FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR UPPER(TRIM(s.empCode)) IN :empCodes) " +
                        "GROUP BY s.iteCode " +
                        "ORDER BY COALESCE(SUM(s.grandTotal), 0) DESC")
        java.util.List<Object[]> findItemByFilters(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") java.util.List<String> empCodes);

        // ==================== TIMELINE / CHART AGGREGATION ====================

        @Query("SELECT FUNCTION('to_char', s.docDate, 'YYYY'), " +
                        "COALESCE(SUM(s.grandTotal), 0), " +
                        "COALESCE(SUM(s.totalHpp), 0), " +
                        "COALESCE(SUM(s.labaKotor), 0) " +
                        "FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR UPPER(TRIM(s.empCode)) IN :empCodes) " +
                        "GROUP BY FUNCTION('to_char', s.docDate, 'YYYY') " +
                        "ORDER BY FUNCTION('to_char', s.docDate, 'YYYY') ASC")
        java.util.List<Object[]> sumTimelineYear(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") java.util.List<String> empCodes);

        @Query("SELECT FUNCTION('to_char', s.docDate, 'YYYY-MM'), " +
                        "COALESCE(SUM(s.grandTotal), 0), " +
                        "COALESCE(SUM(s.totalHpp), 0), " +
                        "COALESCE(SUM(s.labaKotor), 0) " +
                        "FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR UPPER(TRIM(s.empCode)) IN :empCodes) " +
                        "GROUP BY FUNCTION('to_char', s.docDate, 'YYYY-MM') " +
                        "ORDER BY FUNCTION('to_char', s.docDate, 'YYYY-MM') ASC")
        java.util.List<Object[]> sumTimelineMonth(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") java.util.List<String> empCodes);

        @Query("SELECT FUNCTION('to_char', s.docDate, 'YYYY-MM-DD'), " +
                        "COALESCE(SUM(s.grandTotal), 0), " +
                        "COALESCE(SUM(s.totalHpp), 0), " +
                        "COALESCE(SUM(s.labaKotor), 0) " +
                        "FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR UPPER(TRIM(s.empCode)) IN :empCodes) " +
                        "GROUP BY FUNCTION('to_char', s.docDate, 'YYYY-MM-DD') " +
                        "ORDER BY FUNCTION('to_char', s.docDate, 'YYYY-MM-DD') ASC")
        java.util.List<Object[]> sumTimelineDay(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") java.util.List<String> empCodes);

        // ==================== PIUTANG (row-level, spt list Penjualan + tab PAID/UNPAID) =====
        // Status filter di level baris sales. statusBayar dibawa per baris (migrasi).

        @Query("SELECT s FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR s.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR s.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'code' AND LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ") AND " +
                        "(:isPaid = true AND UPPER(TRIM(s.statusBayar)) IN ('PAID','LUNAS','Lunas') " +
                        " OR :isPaid = false AND (s.statusBayar IS NULL OR UPPER(TRIM(s.statusBayar)) IN ('UNPAID','BELUM LUNAS','Belum Lunas','JATUH TEMPO','Jatuh Tempo')))")
        Page<Sales> findPiutangRows(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn,
                        @Param("isPaid") boolean isPaid,
                        Pageable pageable);

        @Query("SELECT s FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR s.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR s.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'code' AND LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ") AND " +
                        "(:isPaid = true AND UPPER(TRIM(s.statusBayar)) IN ('PAID','LUNAS','Lunas') " +
                        " OR :isPaid = false AND (s.statusBayar IS NULL OR UPPER(TRIM(s.statusBayar)) IN ('UNPAID','BELUM LUNAS','Belum Lunas','JATUH TEMPO','Jatuh Tempo'))) " +
                        "ORDER BY s.docDate DESC")
        java.util.List<Sales> findAllPiutangRows(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn,
                        @Param("isPaid") boolean isPaid);

        @Query("SELECT COALESCE(SUM(s.grandTotal), 0) FROM Sales s WHERE " +
                        "(s.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR s.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR s.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'code' AND LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(s.docNo) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(s.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(s.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(s.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ") AND " +
                        "(:isPaid = true AND UPPER(TRIM(s.statusBayar)) IN ('PAID','LUNAS','Lunas') " +
                        " OR :isPaid = false AND (s.statusBayar IS NULL OR UPPER(TRIM(s.statusBayar)) IN ('UNPAID','BELUM LUNAS','Belum Lunas','JATUH TEMPO','Jatuh Tempo')))")
        BigDecimal sumPiutangGrandTotal(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn,
                        @Param("isPaid") boolean isPaid);
}
