package com.stok.anandam.store.core.postgres.repository;

import com.stok.anandam.store.core.postgres.model.Purchase;

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
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

        @Modifying
        @Transactional
        @Query(value = "TRUNCATE TABLE purchases RESTART IDENTITY", nativeQuery = true)
        void truncateTable();

        @Query("SELECT p FROM Purchase p WHERE " +
                        "(p.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR p.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR p.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ")")
        Page<Purchase> findByDateRangeAndSearch(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn,
                        Pageable pageable);

        @Query("SELECT p FROM Purchase p WHERE " +
                        "(p.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR p.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR p.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ") ORDER BY p.docDate DESC")
        java.util.List<Purchase> findAllByDateRangeAndSearch(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn);

        @Query("SELECT COALESCE(SUM(p.grandTotal), 0) FROM Purchase p WHERE " +
                        "(p.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR p.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR p.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ")")
        BigDecimal sumGrandTotalByDateRange(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn);

        @Query("SELECT COALESCE(SUM(p.grandTotal), 0) FROM Purchase p WHERE p.docDate = :today")
        BigDecimal sumTotalByDate(@Param("today") LocalDate today);

        @Query("SELECT COALESCE(SUM(p.qty), 0) FROM Purchase p WHERE " +
                        "(p.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR p.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR p.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ")")
        Long sumQtyByDateRange(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn);

        @Query(value = """
                SELECT DISTINCT ON (TRIM(LOWER(p.item_name)))
                    TRIM(p.item_name)  AS item_name,
                    p.doc_date         AS doc_date,
                    p.par_name         AS par_name,
                    p.price            AS price
                FROM purchases p
                WHERE TRIM(LOWER(p.item_name)) IN (:itemNames)
                ORDER BY TRIM(LOWER(p.item_name)), p.doc_date DESC
                """, nativeQuery = true)
        List<Object[]> findLatestPurchaseDetailsByItemNames(@Param("itemNames") List<String> itemNames);

        @Query(value = "SELECT * FROM purchases WHERE item_name = :itemName ORDER BY doc_date DESC LIMIT 1", nativeQuery = true)
        java.util.Optional<Purchase> findLatestPurchaseByItemName(@Param("itemName") String itemName);

        @Query("SELECT DISTINCT p.depCode FROM Purchase p WHERE p.depCode IS NOT NULL AND TRIM(p.depCode) <> '' ORDER BY p.depCode")
        java.util.List<String> findDistinctDepCodes();

        @Query("SELECT DISTINCT p.empCode, MAX(p.empName) FROM Purchase p WHERE p.empCode IS NOT NULL AND TRIM(p.empCode) <> '' GROUP BY p.empCode ORDER BY p.empCode")
        java.util.List<Object[]> findDistinctEmpCodeAndName();

        // ==================== HUTANG (row-level, spt list Pembelian + tab PAID/UNPAID) =====
        @Query("SELECT p FROM Purchase p WHERE " +
                        "(p.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR p.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR p.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ") AND " +
                        "(:isPaid = true AND UPPER(TRIM(p.statusBayar)) IN ('PAID','LUNAS','Lunas') " +
                        " OR :isPaid = false AND (p.statusBayar IS NULL OR UPPER(TRIM(p.statusBayar)) IN ('UNPAID','BELUM LUNAS','Belum Lunas','JATUH TEMPO','Jatuh Tempo')))")
        Page<Purchase> findHutangRows(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn,
                        @Param("isPaid") boolean isPaid,
                        Pageable pageable);

        @Query("SELECT COALESCE(SUM(p.grandTotal), 0) FROM Purchase p WHERE " +
                        "(p.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR p.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR p.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ") AND " +
                        "(:isPaid = true AND UPPER(TRIM(p.statusBayar)) IN ('PAID','LUNAS','Lunas') " +
                        " OR :isPaid = false AND (p.statusBayar IS NULL OR UPPER(TRIM(p.statusBayar)) IN ('UNPAID','BELUM LUNAS','Belum Lunas','JATUH TEMPO','Jatuh Tempo')))")
        BigDecimal sumHutangGrandTotal(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn,
                        @Param("isPaid") boolean isPaid);

        @Query("SELECT p FROM Purchase p WHERE " +
                        "(p.docDate BETWEEN :startDate AND :endDate) AND " +
                        "(:empCodes IS NULL OR p.empCode IN :empCodes) AND " +
                        "(:categories IS NULL OR p.depCode IN :categories) AND " +
                        "(:search IS NULL OR :search = '' OR " +
                        "((:searchColumn IS NULL OR :searchColumn = 'ALL' OR :searchColumn = '') AND (LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%')))) OR " +
                        "(:searchColumn = 'noNota' AND LOWER(p.docNoP) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'distributor' AND LOWER(p.parName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'barang' AND LOWER(p.itemName) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
                        "(:searchColumn = 'dept' AND LOWER(p.depCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        ") AND " +
                        "(:isPaid = true AND UPPER(TRIM(p.statusBayar)) IN ('PAID','LUNAS','Lunas') " +
                        " OR :isPaid = false AND (p.statusBayar IS NULL OR UPPER(TRIM(p.statusBayar)) IN ('UNPAID','BELUM LUNAS','Belum Lunas','JATUH TEMPO','Jatuh Tempo'))) " +
                        "ORDER BY p.docDate DESC")
        java.util.List<Purchase> findAllHutangRows(
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("empCodes") List<String> empCodes,
                        @Param("categories") List<String> categories,
                        @Param("search") String search,
                        @Param("searchColumn") String searchColumn,
                        @Param("isPaid") boolean isPaid);
}