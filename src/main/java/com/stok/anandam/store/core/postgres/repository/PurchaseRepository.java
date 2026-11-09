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



        @Query("SELECT COALESCE(SUM(p.grandTotal), 0) FROM Purchase p WHERE p.docDate = :today")
        BigDecimal sumTotalByDate(@Param("today") LocalDate today);


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

}
