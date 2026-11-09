package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.Purchase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Query pencarian Pembelian (dan Hutang) dengan predikat dinamis.
 *
 * <p>P1 (9 Nov 2026): menggantikan method {@code @Query} yang memakai pola
 * {@code (:param IS NULL OR ...)}. Hanya kondisi yang dipakai yang masuk JPQL,
 * sehingga rencana query tidak lagi generik dan index bisa dipakai.</p>
 */
@Component
public class PurchaseSearchDao {

    private static final Set<String> SORTABLE = Set.of(
            "docDate", "docNoP", "parName", "itemName", "qty", "price",
            "grandTotal", "empCode", "depCode");

    @PersistenceContext
    private EntityManager entityManager;

    /** Halaman list pembelian + urutan dari Pageable (tanpa COUNT query). */
    public List<Purchase> findPage(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn, Pageable pageable) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn);
        TypedQuery<Purchase> query = entityManager.createQuery(
                "SELECT p FROM Purchase p" + filter.whereClause()
                        + filter.orderBy(pageable, "docDate", SORTABLE),
                Purchase.class);
        filter.bind(query);
        if (pageable != null) {
            query.setFirstResult(Math.toIntExact(pageable.getOffset()));
            query.setMaxResults(pageable.getPageSize());
        }
        return query.getResultList();
    }

    /** Semua baris pembelian (export) dengan urutan tanggal terbaru. */
    public List<Purchase> findAll(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn);
        TypedQuery<Purchase> query = entityManager.createQuery(
                "SELECT p FROM Purchase p" + filter.whereClause() + " ORDER BY p.docDate DESC",
                Purchase.class);
        filter.bind(query);
        return query.getResultList();
    }

    /**
     * Satu query untuk COUNT + SUM(grandTotal) + SUM(qty).
     * Hasil: [count, sumGrandTotal, sumQty].
     */
    public Object[] aggregate(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn);
        TypedQuery<Object[]> query = entityManager.createQuery(
                "SELECT COUNT(p), COALESCE(SUM(p.grandTotal), 0), COALESCE(SUM(p.qty), 0) "
                        + "FROM Purchase p" + filter.whereClause(),
                Object[].class);
        filter.bind(query);
        return query.getSingleResult();
    }

    /** Halaman Hutang (status bayar) + total, memakai predikat dinamis. */
    public PagedResult<Purchase> findHutangPage(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn, boolean isPaid,
            Pageable pageable) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn)
                .paymentStatus("statusBayar", isPaid);

        TypedQuery<Long> countQuery = entityManager.createQuery(
                "SELECT COUNT(p) FROM Purchase p" + filter.whereClause(), Long.class);
        filter.bind(countQuery);
        long total = countQuery.getSingleResult();

        TypedQuery<Purchase> query = entityManager.createQuery(
                "SELECT p FROM Purchase p" + filter.whereClause()
                        + filter.orderBy(pageable, "docDate", SORTABLE),
                Purchase.class);
        filter.bind(query);
        if (pageable != null) {
            query.setFirstResult(Math.toIntExact(pageable.getOffset()));
            query.setMaxResults(pageable.getPageSize());
        }
        return new PagedResult<>(query.getResultList(), total);
    }

    /** Semua baris hutang (export). */
    public List<Purchase> findAllHutang(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn, boolean isPaid) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn)
                .paymentStatus("statusBayar", isPaid);
        TypedQuery<Purchase> query = entityManager.createQuery(
                "SELECT p FROM Purchase p" + filter.whereClause() + " ORDER BY p.docDate DESC",
                Purchase.class);
        filter.bind(query);
        return query.getResultList();
    }

    /** Total grand total hutang (satu query SUM). */
    public java.math.BigDecimal sumHutangGrandTotal(LocalDate start, LocalDate end,
            List<String> empCodes, List<String> categories, String search, String searchColumn,
            boolean isPaid) {
        JpqlSearchFilter filter = baseFilter(start, end, empCodes, categories, search, searchColumn)
                .paymentStatus("statusBayar", isPaid);
        TypedQuery<java.math.BigDecimal> query = entityManager.createQuery(
                "SELECT COALESCE(SUM(p.grandTotal), 0) FROM Purchase p" + filter.whereClause(),
                java.math.BigDecimal.class);
        filter.bind(query);
        java.math.BigDecimal result = query.getSingleResult();
        return result == null ? java.math.BigDecimal.ZERO : result;
    }

    private JpqlSearchFilter baseFilter(LocalDate start, LocalDate end, List<String> empCodes,
            List<String> categories, String search, String searchColumn) {
        return new JpqlSearchFilter("p")
                .dateRange(start, end)
                .in("empCode", "empCodes", empCodes)
                .in("depCode", "categories", categories)
                .search("docNoP", "parName", "itemName", "depCode", null, search, searchColumn);
    }
}
