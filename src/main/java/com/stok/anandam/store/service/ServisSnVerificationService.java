package com.stok.anandam.store.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/** Server-side source of truth for serial-number locks on service notes. */
@Service
public class ServisSnVerificationService {
    private final JdbcTemplate jdbcTemplate;

    public ServisSnVerificationService(@Qualifier("pgJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Verifikasi SN + cari supplier.
     *
     * P0 (8 Nov 2026): correlated subquery supplier dihapus. Verifikasi hanya
     * mengembalikan satu baris, jadi supplier diambil dengan query terpisah
     * yang memakai index {@code idx_purchases_docno_btrim}. Kedua query juga
     * memakai index normalisasi SN ({@code idx_isn_sn_norm}).
     */
    public Verification verify(String serialNumber) {
        if (serialNumber == null || serialNumber.isBlank()) return Verification.notVerified();

        List<SnRow> rows = jdbcTemplate.query("""
            SELECT s.item_name, s.doc_id
            FROM item_serial_numbers s
            WHERE upper(s.type) = 'MASUK' AND upper(btrim(s.sn)) = upper(btrim(?))
            ORDER BY s.tanggal DESC NULLS LAST LIMIT 1
            """,
                (rs, row) -> new SnRow(rs.getString("item_name"), rs.getString("doc_id")),
                serialNumber);
        if (rows.isEmpty()) {
            return Verification.notVerified();
        }

        SnRow row = rows.get(0);
        String supplier = null;
        if (row.docId() != null && !row.docId().isBlank()) {
            List<String> suppliers = jdbcTemplate.query("""
                SELECT p.par_name
                FROM purchases p
                WHERE btrim(p.doc_no_p) = btrim(?)
                  AND p.par_name IS NOT NULL
                  AND upper(btrim(p.par_name)) NOT IN ('PERSEDIAAN AWAL', 'CASH')
                LIMIT 1
                """,
                    (rs, r) -> rs.getString("par_name"),
                    row.docId());
            if (!suppliers.isEmpty()) {
                supplier = suppliers.get(0);
            }
        }
        return new Verification(true, row.itemName(), supplier);
    }

    private record SnRow(String itemName, String docId) {
    }

    public record Verification(boolean verified, String itemName, String supplier) {
        static Verification notVerified() { return new Verification(false, null, null); }
    }
}
