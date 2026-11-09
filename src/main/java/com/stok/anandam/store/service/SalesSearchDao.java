package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.Sales;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Query pencarian Penjualan (dan Piutang) dengan predikat dinamis.
 *
 * <p>P1 (9 Nov 2026): menggantikan method {@code @Query} yang memakai pola
 * {@code (:param IS NULL OR ...)}. Hanya kondisi yang dipakai yang masuk JPQL,
 * sehingga rencana query tidak lagi generik dan index bisa dipakai.</p>
 */
@Component
public class SalesSearchDao {

    private static final Set<String> SORTABLE = Set.of(
            "docDate", "docNo", "code", "parName", "itemName", "qty", "price",
            "grandTotal", "empCode", "depCode", "iteCode");

    @PersistenceContext
    private EntityManager entityManager;

    /** Halaman list penjualan + urutan dari Pageable (tanpa COUNT query). */
    public List<Sales> findPage(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn, Pageable pageable) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn);
        String jpql = "SELECT s FROM Sales s" + filter.whereClause()
                + filter.orderBy(pageable, "docDate", SORTABLE);
        TypedQuery<Sales> query = entityManager.createQuery(jpql, Sales.class);
        filter.bind(query);
        if (pageable != null) {
            query.setFirstResult(Math.toIntExact(pageable.getOffset()));
            query.setMaxResults(pageable.getPageSize());
        }
        return query.getResultList();
    }

    /** Semua baris penjualan (export) dengan urutan tanggal terbaru. */
    public List<Sales> findAll(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn);
        TypedQuery<Sales> query = entityManager.createQuery(
                "SELECT s FROM Sales s" + filter.whereClause() + " ORDER BY s.docDate DESC", Sales.class);
        filter.bind(query);
        return query.getResultList();
    }

    /**
     * Satu query untuk COUNT + 4 SUM.
     * Hasil: [count, sumGrandTotal, sumQty, sumTotalHpp, sumLabaKotor].
     */
    public Object[] aggregate(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn);
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT COUNT(s), COALESCE(SUM(s.grandTotal), 0), COALESCE(SUM(s.qty), 0), "
                        + "COALESCE(SUM(s.totalHpp), 0), COALESCE(SUM(s.labaKotor), 0) "
                        + "FROM Sales s" + filter.whereClause(),
                Object[].class);
        filter.bind(query);
        return query.getSingleResult();
    }

    /** Halaman Piutang (status bayar) + total, memakai predikat dinamis. */
    public PagedResult<Sales> findPiutangPage(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn, boolean isPaid,
            Pageable pageable) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn)
                .paymentStatus("statusBayar", isPaid);

        TypedQuery<Long> countQuery = entityManager.createQuery(
                "SELECT COUNT(s) FROM Sales s" + filter.whereClause(), Long.class);
        filter.bind(countQuery);
        long total = countQuery.getSingleResult();

        TypedQuery<Sales> query = entityManager.createQuery(
                "SELECT s FROM Sales s" + filter.whereClause()
                        + filter.orderBy(pageable, "docDate", SORTABLE),
                Sales.class);
        filter.bind(query);
        if (pageable != null) {
            query.setFirstResult(Math.toIntExact(pageable.getOffset()));
            query.setMaxResults(pageable.getPageSize());
        }
        return new PagedResult<>(query.getResultList(), total);
    }

    /** Semua baris piutang (export). */
    public List<Sales> findAllPiutang(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn, boolean isPaid) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn)
                .paymentStatus("statusBayar", isPaid);
        TypedQuery<Sales> query = entityManager.createQuery(
                "SELECT s FROM Sales s" + filter.whereClause() + " ORDER BY s.docDate DESC", Sales.class);
        filter.bind(query);
        return query.getResultList();
    }

    /** Total grand total piutang (satu query SUM). */
    public java.math.BigDecimal sumPiutangGrandTotal(LocalDate start, LocalDate end,
            List<String> empCodes, List<String> categories, String search, String searchColumn,
            boolean isPaid) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn)
                .paymentStatus("statusBayar", isPaid);
        TypedQuery<java.math.BigDecimal> query = entityManager.createQuery(
                "SELECT COALESCE(SUM(s.grandTotal), 0) FROM Sales s" + filter.whereClause(),
                java.math.BigDecimal.class);
        filter.bind(query);
        java.math.BigDecimal result = query.getSingleResult();
        return result == null ? java.math.BigDecimal.ZERO : result;
    }

    private JpqlSearchFilter baseFilter(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn) {
        return new JpqlSearchFilter("s")
                .dateRange(start, end)
                .in("empCode", "empCodes", empCodes)
                .in("depCode", "categories", categories)
                .search("docNo", "parName", "itemName", "depCode", "code", search, searchColumn);
    }
}
