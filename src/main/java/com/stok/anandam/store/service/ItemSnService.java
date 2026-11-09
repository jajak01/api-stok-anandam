package com.stok.anandam.store.service;

import com.stok.anandam.store.dto.ItemSerialNumberResponse;
import com.stok.anandam.store.dto.SnVerificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ItemSnService {

    private static final Logger log = LoggerFactory.getLogger(ItemSnService.class);

    /** Field yang boleh dipakai untuk sortBy (aman dari SQL injection). */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("tanggal", "docId", "user", "itemName", "sn");

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    /** Halaman hasil SN: baris + total, diambil dari SATU query (COUNT(*) OVER()). */
    public static class SnPage {
        private final List<ItemSerialNumberResponse> items;
        private final long total;

        public SnPage(List<ItemSerialNumberResponse> items, long total) {
            this.items = items;
            this.total = total;
        }

        public List<ItemSerialNumberResponse> getItems() {
            return items;
        }

        public long getTotal() {
            return total;
        }
    }

    /**
     * Ambil data SN (Masuk/Keluar) dengan filter lengkap.
     * Data diambil dari tabel item_serial_numbers di PostgreSQL.
     *
     * P0 (8 Nov 2026):
     * - list + count digabung menjadi satu query (COUNT(*) OVER()).
     * - subquery supplier per baris dihapus; supplier diambil sekali untuk
     *   seluruh halaman (max `size` dokumen) lalu di-join di Java.
     *
     * @param type      MASUK atau KELUAR
     * @param search    Pencarian global (sn, doc_id, user_name, item_name)
     * @param docId     Filter nomor dokumen (partial)
     * @param user      Filter nama user/user_name (partial)
     * @param itemName  Filter nama barang (partial)
     * @param sn        Filter serial number (partial)
     * @param startDate Filter tanggal mulai (yyyy-MM-dd)
     * @param endDate   Filter tanggal akhir (yyyy-MM-dd)
     * @param sortBy    Field sort: tanggal, docId, user, itemName, sn
     * @param direction asc atau desc
     */
    public SnPage getSnDataPage(String type, String search, String docId, String user,
            String itemName, String sn, String startDate, String endDate,
            String sortBy, String direction, int size, int offset) {

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        sql.append("SELECT i.tanggal, i.doc_id, i.user_name, i.item_name, i.sn, i.type AS doc_type, ")
           .append("COUNT(*) OVER() AS total_count ")
           .append("FROM item_serial_numbers i ")
           .append("WHERE i.sn IS NOT NULL AND TRIM(i.sn) <> '' ");

        // Filter: type (MASUK / KELUAR)
        if (type != null && !type.isBlank()) {
            sql.append("AND UPPER(i.type) = ? ");
            params.add(type.toUpperCase());
        }

        applyFilters(sql, params, search, docId, user, itemName, sn, startDate, endDate);

        // Sort: whitelist field -> kolom asli (aman dari SQL injection)
        String orderColumn = "tanggal";
        if (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) {
            switch (sortBy) {
                case "docId":
                    orderColumn = "doc_id";
                    break;
                case "user":
                    orderColumn = "user_name";
                    break;
                case "itemName":
                    orderColumn = "item_name";
                    break;
                case "sn":
                    orderColumn = "sn";
                    break;
                case "tanggal":
                default:
                    orderColumn = "tanggal";
                    break;
            }
        }

        String dir = "desc".equalsIgnoreCase(direction) ? "DESC" : "ASC";
        sql.append("ORDER BY ").append(orderColumn).append(" ").append(dir)
           .append(" NULLS LAST LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);

        log.debug("getSnDataPage SQL: {}", sql);

        final long[] totalHolder = new long[1];
        List<ItemSerialNumberResponse> items = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> {
                    totalHolder[0] = rs.getLong("total_count");
                    return ItemSerialNumberResponse.builder()
                            .tanggal(rs.getObject("tanggal", LocalDateTime.class))
                            .docId(rs.getString("doc_id"))
                            .user(rs.getString("user_name"))
                            .itemName(rs.getString("item_name"))
                            .sn(rs.getString("sn"))
                            .docType(rs.getString("doc_type"))
                            .build();
                },
                params.toArray());

        attachSuppliers(items);
        return new SnPage(items, totalHolder[0]);
    }

    /** Backward-compatible helper: hanya daftar baris. */
    public List<ItemSerialNumberResponse> getSnData(String type, String search, String docId, String user,
            String itemName, String sn, String startDate, String endDate,
            String sortBy, String direction, int size, int offset) {
        return getSnDataPage(type, search, docId, user, itemName, sn, startDate, endDate,
                sortBy, direction, size, offset).getItems();
    }

    /**
     * P1: verifikasi SATU serial number secara exact match di SN Masuk (BL),
     * SN Keluar (JL), dan data lama, sekaligus dalam satu response.
     *
     * <p>Tanpa count query: masing-masing sumber dibatasi 50 baris terbaru
     * (cukup untuk kebutuhan verifikasi nota servis).</p>
     */
    public SnVerificationResponse verifyExact(String sn) {
        String normalized = trimToNull(sn);
        if (normalized == null) {
            return SnVerificationResponse.builder()
                    .sn("")
                    .masuk(List.of())
                    .keluar(List.of())
                    .old(List.of())
                    .verified(false)
                    .build();
        }

        List<ItemSerialNumberResponse> masuk = queryExact("MASUK", normalized);
        List<ItemSerialNumberResponse> keluar = queryExact("KELUAR", normalized);
        attachSuppliers(masuk);

        List<ItemSerialNumberResponse> old = queryOldExact(normalized);

        return SnVerificationResponse.builder()
                .sn(normalized)
                .masuk(masuk)
                .keluar(keluar)
                .old(old)
                .verified(!masuk.isEmpty() || !keluar.isEmpty() || !old.isEmpty())
                .build();
    }

    /** Query exact match pada item_serial_numbers untuk satu tipe (MASUK/KELUAR). */
    private List<ItemSerialNumberResponse> queryExact(String type, String sn) {
        String sql = "SELECT i.tanggal, i.doc_id, i.user_name, i.item_name, i.sn, i.type AS doc_type "
                + "FROM item_serial_numbers i "
                + "WHERE UPPER(i.type) = ? AND UPPER(BTRIM(i.sn)) = UPPER(BTRIM(?)) "
                + "ORDER BY i.tanggal DESC NULLS LAST LIMIT 50";
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> ItemSerialNumberResponse.builder()
                        .tanggal(rs.getObject("tanggal", LocalDateTime.class))
                        .docId(rs.getString("doc_id"))
                        .user(rs.getString("user_name"))
                        .itemName(rs.getString("item_name"))
                        .sn(rs.getString("sn"))
                        .docType(rs.getString("doc_type"))
                        .build(),
                type, sn);
    }

    /** Query exact match pada old_item_serial_numbers (BL lama). */
    private List<ItemSerialNumberResponse> queryOldExact(String sn) {
        String sql = "SELECT tanggal, doc_id, user_name, item_name, sn, type AS doc_type "
                + "FROM old_item_serial_numbers "
                + "WHERE UPPER(BTRIM(sn)) = UPPER(BTRIM(?)) "
                + "ORDER BY tanggal DESC NULLS LAST LIMIT 50";
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> ItemSerialNumberResponse.builder()
                        .tanggal(rs.getObject("tanggal", LocalDateTime.class))
                        .docId(rs.getString("doc_id"))
                        .user(rs.getString("user_name"))
                        .itemName(rs.getString("item_name"))
                        .sn(rs.getString("sn"))
                        .docType(rs.getString("doc_type"))
                        .build(),
                sn);
    }

    public long countSnData(String type, String search, String docId, String user,
            String itemName, String sn, String startDate, String endDate) {

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        sql.append("SELECT COUNT(*) FROM item_serial_numbers ")
           .append("WHERE sn IS NOT NULL AND TRIM(sn) <> '' ");

        // Filter: type (MASUK / KELUAR)
        if (type != null && !type.isBlank()) {
            sql.append("AND UPPER(type) = ? ");
            params.add(type.toUpperCase());
        }

        applyFilters(sql, params, search, docId, user, itemName, sn, startDate, endDate);

        log.debug("countSnData SQL: {}", sql);

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    /**
     * Ambil supplier untuk seluruh baris halaman dalam SATU query, bukan satu
     * subquery per baris. Aturan sama dengan query lama: cocokkan doc_no_p dan
     * item_name (item_name purchases NULL = cocok untuk semua), ambil yang
     * paling baru berdasarkan doc_date.
     */
    private void attachSuppliers(List<ItemSerialNumberResponse> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        LinkedHashSet<String> docIds = new LinkedHashSet<>();
        for (ItemSerialNumberResponse item : items) {
            String docId = trimToNull(item.getDocId());
            if (docId != null) {
                docIds.add(docId);
            }
        }
        if (docIds.isEmpty()) {
            return;
        }

        String placeholders = String.join(",", Collections.nCopies(docIds.size(), "?"));
        String sql = "SELECT btrim(doc_no_p) AS doc_id, upper(btrim(item_name)) AS item_key, par_name "
                + "FROM purchases "
                + "WHERE btrim(doc_no_p) IN (" + placeholders + ") AND par_name IS NOT NULL "
                + "ORDER BY doc_date DESC NULLS LAST";

        Map<String, List<String[]>> byDoc = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String docId = rs.getString("doc_id");
            if (docId == null) {
                return;
            }
            String key = rs.getString("item_key");
            String supplier = rs.getString("par_name");
            byDoc.computeIfAbsent(docId, k -> new ArrayList<>()).add(new String[] { key, supplier });
        }, docIds.toArray());

        for (ItemSerialNumberResponse item : items) {
            String docId = trimToNull(item.getDocId());
            if (docId == null) {
                continue;
            }
            List<String[]> candidates = byDoc.get(docId);
            if (candidates == null) {
                continue;
            }
            String itemKey = item.getItemName() == null
                    ? null
                    : item.getItemName().trim().toUpperCase(Locale.ROOT);
            for (String[] candidate : candidates) {
                String candidateKey = candidate[0];
                if (candidateKey == null || candidateKey.equals(itemKey)) {
                    item.setSupplier(candidate[1]);
                    break;
                }
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void applyFilters(StringBuilder sql, List<Object> params, String search, String docId, String user,
            String itemName, String sn, String startDate, String endDate) {

        // Filter: search global (semua field)
        if (search != null && !search.isBlank()) {
            String term = "%" + search.trim().toLowerCase() + "%";
            sql.append("AND (LOWER(sn) LIKE ? OR LOWER(doc_id) LIKE ? OR LOWER(user_name) LIKE ? OR LOWER(item_name) LIKE ?) ");
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }

        // Filter: docId (nomor dokumen)
        if (docId != null && !docId.isBlank()) {
            sql.append("AND LOWER(doc_id) LIKE ? ");
            params.add("%" + docId.trim().toLowerCase() + "%");
        }

        // Filter: user (nama user/user_name)
        if (user != null && !user.isBlank()) {
            sql.append("AND LOWER(user_name) LIKE ? ");
            params.add("%" + user.trim().toLowerCase() + "%");
        }

        // Filter: itemName (nama barang)
        if (itemName != null && !itemName.isBlank()) {
            sql.append("AND LOWER(item_name) LIKE ? ");
            params.add("%" + itemName.trim().toLowerCase() + "%");
        }

        // Filter: sn (serial number)
        if (sn != null && !sn.isBlank()) {
            sql.append("AND LOWER(sn) LIKE ? ");
            params.add("%" + sn.trim().toLowerCase() + "%");
        }

        // Filter: date range
        if (startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
            sql.append("AND (tanggal BETWEEN ?::timestamp AND ?::timestamp) ");
            params.add(startDate + " 00:00:00");
            params.add(endDate + " 23:59:59");
        }
    }
}
