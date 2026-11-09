package com.stok.anandam.store.service;

import com.stok.anandam.store.dto.SuggestItemResponse;
import com.stok.anandam.store.util.NormalizationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Master nama barang, supplier pembelian, dan distributor klaim untuk modul Servis.
 *
 * <p>Semua saran memakai data yang sudah ter-seed di PostgreSQL:
 * <ul>
 *   <li>{@code master_barang} (7 sumber historis, ±24 ribu nama)</li>
 *   <li>{@code purchases.par_name} / {@code old_purchase.par_name} (supplier)</li>
 *   <li>{@code klaim_distributor.nama_distributor} (tujuan klaim, free text)</li>
 * </ul>
 * Cache in-memory 5 menit; ukuran maksimum 1.000 query untuk mencegah memory leak.
 */
@Service
public class ServisMasterService {

    private static final Logger log = LoggerFactory.getLogger(ServisMasterService.class);

    private static final int MAX_LIMIT = 50;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_CACHE_ENTRIES = 1_000;

    private static final String SQL_BARANG_PREFIX = """
            SELECT nama_tampil, normalized_name, jumlah_pakai, last_seen_at
            FROM master_barang
            WHERE is_active = true
              AND normalized_name LIKE ?
            ORDER BY jumlah_pakai DESC NULLS LAST, last_seen_at DESC NULLS LAST
            LIMIT ?
            """;

    private static final String SQL_SUPPLIER_RIWAYAT = """
            WITH p AS (
                SELECT btrim(par_name) AS nama, doc_date::timestamp AS tanggal
                FROM purchases
                WHERE par_name IS NOT NULL AND btrim(par_name) <> ''
                  AND movva_normalize_item(item_name) = ?
                UNION ALL
                SELECT btrim(par_name), doc_date::timestamp
                FROM old_purchase
                WHERE par_name IS NOT NULL AND btrim(par_name) <> ''
                  AND movva_normalize_item(item_name) = ?
            )
            SELECT nama, count(*) AS jumlah, max(tanggal) AS terakhir
            FROM p
            WHERE upper(nama) NOT IN ('PERSEDIAAN AWAL', 'CASH')
            GROUP BY nama
            ORDER BY terakhir DESC NULLS LAST, jumlah DESC
            LIMIT ?
            """;

    private static final String SQL_SUPPLIER_MASTER = """
            SELECT d.nama_distributor AS nama,
                   COALESCE(d.normalized_name, movva_normalize_item(d.nama_distributor)) AS normalized
            FROM distributor d
            WHERE COALESCE(d.is_active, true) = true
              AND (
                    COALESCE(d.normalized_name, movva_normalize_item(d.nama_distributor)) LIKE ?
                 OR EXISTS (
                      SELECT 1 FROM distributor_alias a
                      WHERE a.distributor_id = d.id AND a.alias_norm LIKE ?
                 )
              )
            ORDER BY d.nama_distributor
            LIMIT ?
            """;

    private static final String SQL_SUPPLIER_PARNAME = """
            WITH p AS (
                SELECT btrim(par_name) AS nama, doc_date::timestamp AS tanggal
                FROM purchases
                WHERE par_name IS NOT NULL AND btrim(par_name) <> ''
                  AND upper(btrim(par_name)) LIKE ?
                UNION ALL
                SELECT btrim(par_name), doc_date::timestamp
                FROM old_purchase
                WHERE par_name IS NOT NULL AND btrim(par_name) <> ''
                  AND upper(btrim(par_name)) LIKE ?
            )
            SELECT nama, count(*) AS jumlah, max(tanggal) AS terakhir
            FROM p
            WHERE upper(nama) NOT IN ('PERSEDIAAN AWAL', 'CASH')
            GROUP BY nama
            ORDER BY terakhir DESC NULLS LAST, jumlah DESC
            LIMIT ?
            """;

    private static final String SQL_DISTRIBUTOR_KLAIM = """
            SELECT btrim(nama_distributor) AS nama,
                   count(*) AS jumlah,
                   max(coalesce(updated_at, created_at)) AS terakhir
            FROM klaim_distributor
            WHERE nama_distributor IS NOT NULL
              AND btrim(nama_distributor) <> ''
              AND (? = '' OR upper(btrim(nama_distributor)) LIKE ?)
            GROUP BY btrim(nama_distributor)
            ORDER BY jumlah DESC, nama
            LIMIT ?
            """;

    private static final String SQL_SEED_MASTER = """
            WITH src AS (
                SELECT item_name, doc_date::timestamp AS dt FROM purchases WHERE item_name IS NOT NULL AND btrim(item_name) <> ''
                UNION ALL SELECT item_name, doc_date::timestamp FROM old_purchase WHERE item_name IS NOT NULL AND btrim(item_name) <> ''
                UNION ALL SELECT item_name, doc_date::timestamp FROM sales WHERE item_name IS NOT NULL AND btrim(item_name) <> ''
                UNION ALL SELECT item_name, doc_date::timestamp FROM old_sales WHERE item_name IS NOT NULL AND btrim(item_name) <> ''
                UNION ALL SELECT item_name, last_synced FROM stok WHERE item_name IS NOT NULL AND btrim(item_name) <> ''
                UNION ALL SELECT item_name, tanggal FROM item_serial_numbers WHERE item_name IS NOT NULL AND btrim(item_name) <> ''
                UNION ALL SELECT item_name, tanggal FROM old_item_serial_numbers WHERE item_name IS NOT NULL AND btrim(item_name) <> ''
            ), n AS (
                SELECT item_name, dt, movva_normalize_item(item_name) AS norm
                FROM src WHERE btrim(item_name) <> ''
            )
            INSERT INTO master_barang (nama_tampil, normalized_name, sumber, jumlah_pakai, last_seen_at)
            SELECT left(mode() WITHIN GROUP (ORDER BY item_name), 500), norm, 'CAMPURAN', count(*), max(dt)
            FROM n
            WHERE norm <> ''
            GROUP BY norm
            ON CONFLICT (normalized_name) DO UPDATE SET
                jumlah_pakai = EXCLUDED.jumlah_pakai,
                last_seen_at = GREATEST(COALESCE(master_barang.last_seen_at, EXCLUDED.last_seen_at), EXCLUDED.last_seen_at)
            """;

    private static final String SQL_ALIAS_SUPPLIER = """
            WITH p AS (
                SELECT DISTINCT btrim(par_name) AS alias
                FROM purchases
                WHERE par_name IS NOT NULL AND btrim(par_name) <> ''
                UNION
                SELECT DISTINCT btrim(par_name)
                FROM old_purchase
                WHERE par_name IS NOT NULL AND btrim(par_name) <> ''
            )
            INSERT INTO distributor_alias (distributor_id, alias, alias_norm)
            SELECT d.id, p.alias, movva_normalize_item(p.alias)
            FROM p
            JOIN distributor d ON upper(btrim(d.nama_distributor)) = upper(p.alias)
            WHERE movva_normalize_item(p.alias) <> ''
            ON CONFLICT (alias_norm) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ServisMasterService(@Qualifier("pgJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------------------------------------------------------------
    // Saran nama barang
    // ---------------------------------------------------------------------

    public List<SuggestItemResponse> suggestBarang(String q, int limit) {
        String nq = NormalizationUtil.normalizeItemName(q);
        if (nq.length() < 3) {
            return List.of();
        }
        int lim = clampLimit(limit);
        return cached("barang|" + nq + "|" + lim, () -> {
            List<SuggestItemResponse> prefix = jdbcTemplate.query(
                    SQL_BARANG_PREFIX, this::mapBarang, nq + "%", lim);
            if (prefix.size() >= 5) {
                return prefix;
            }
            List<SuggestItemResponse> contains = jdbcTemplate.query(
                    SQL_BARANG_PREFIX, this::mapBarang, "%" + nq + "%", lim);
            return mergeDistinct(prefix, contains, lim);
        });
    }

    private SuggestItemResponse mapBarang(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp seen = rs.getTimestamp("last_seen_at");
        return SuggestItemResponse.builder()
                .nama(rs.getString("nama_tampil"))
                .normalized(rs.getString("normalized_name"))
                .sumber("MASTER")
                .jumlah(rs.getLong("jumlah_pakai"))
                .terakhir(seen == null ? null : seen.toLocalDateTime())
                .build();
    }

    // ---------------------------------------------------------------------
    // Saran supplier pembelian
    // ---------------------------------------------------------------------

    public List<SuggestItemResponse> suggestSupplier(String q, String namaBarang, int limit) {
        String nq = NormalizationUtil.normalizeItemName(q);
        String itemNorm = NormalizationUtil.normalizeItemName(namaBarang);
        if (nq.isEmpty() && itemNorm.isEmpty()) {
            return List.of();
        }
        int lim = clampLimit(limit);
        return cached("supplier|" + nq + "|" + itemNorm + "|" + lim, () -> {
            List<SuggestItemResponse> result = new ArrayList<>();
            if (!itemNorm.isEmpty()) {
                result.addAll(jdbcTemplate.query(
                        SQL_SUPPLIER_RIWAYAT, this::mapSupplierRiwayat,
                        itemNorm, itemNorm, lim));
            }
            if (nq.length() >= 2) {
                // Prefix par_name historis (termasuk varian PDB-/SGI-/SSS-).
                List<SuggestItemResponse> parNames = jdbcTemplate.query(
                        SQL_SUPPLIER_PARNAME, this::mapSupplierParName,
                        nq + "%", nq + "%", lim);
                if (parNames.size() < 5) {
                    List<SuggestItemResponse> contains = jdbcTemplate.query(
                            SQL_SUPPLIER_PARNAME, this::mapSupplierParName,
                            "%" + nq + "%", "%" + nq + "%", lim);
                    parNames = mergeDistinct(parNames, contains, lim);
                }
                List<SuggestItemResponse> master = jdbcTemplate.query(
                        SQL_SUPPLIER_MASTER, this::mapSupplierMaster,
                        nq + "%", nq + "%", lim);
                if (master.size() < 5) {
                    List<SuggestItemResponse> contains = jdbcTemplate.query(
                            SQL_SUPPLIER_MASTER, this::mapSupplierMaster,
                            "%" + nq + "%", "%" + nq + "%", lim);
                    master = mergeDistinct(master, contains, lim);
                }
                result = mergeDistinct(result, parNames, lim);
                result = mergeDistinct(result, master, lim);
            }
            return result;
        });
    }

    private SuggestItemResponse mapSupplierRiwayat(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp terakhir = rs.getTimestamp("terakhir");
        String nama = rs.getString("nama");
        return SuggestItemResponse.builder()
                .nama(nama)
                .normalized(NormalizationUtil.normalizeItemName(nama))
                .sumber("RIWAYAT")
                .jumlah(rs.getLong("jumlah"))
                .terakhir(terakhir == null ? null : terakhir.toLocalDateTime())
                .build();
    }

    private SuggestItemResponse mapSupplierParName(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return mapSupplierName(rs, "RIWAYAT");
    }

    private SuggestItemResponse mapDistributorSupplier(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return mapSupplierName(rs, "SUPPLIER");
    }

    private SuggestItemResponse mapSupplierName(java.sql.ResultSet rs, String sumber) throws java.sql.SQLException {
        Timestamp terakhir = rs.getTimestamp("terakhir");
        String nama = rs.getString("nama");
        return SuggestItemResponse.builder()
                .nama(nama)
                .normalized(NormalizationUtil.normalizeItemName(nama))
                .sumber(sumber)
                .jumlah(rs.getLong("jumlah"))
                .terakhir(terakhir == null ? null : terakhir.toLocalDateTime())
                .build();
    }

    private SuggestItemResponse mapSupplierMaster(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String nama = rs.getString("nama");
        return SuggestItemResponse.builder()
                .nama(nama)
                .normalized(rs.getString("normalized"))
                .sumber("MASTER")
                .jumlah(0L)
                .terakhir(null)
                .build();
    }

    // ---------------------------------------------------------------------
    // Saran distributor klaim
    // ---------------------------------------------------------------------

    public List<SuggestItemResponse> suggestDistributor(String q, int limit) {
        String nq = normalizeDistributor(q);
        int lim = clampLimit(limit);
        return cached("distributor|" + nq + "|" + lim, () -> {
            List<SuggestItemResponse> history = jdbcTemplate.query(
                    SQL_DISTRIBUTOR_KLAIM, this::mapDistributor,
                    nq, nq.isEmpty() ? "" : nq + "%", lim);
            if (!nq.isEmpty() && history.size() < 5) {
                List<SuggestItemResponse> contains = jdbcTemplate.query(
                        SQL_DISTRIBUTOR_KLAIM, this::mapDistributor,
                        nq, "%" + nq + "%", lim);
                history = mergeDistinct(history, contains, lim);
            }
            if (!nq.isEmpty() && history.size() < 5) {
                List<SuggestItemResponse> supplier = jdbcTemplate.query(
                        SQL_SUPPLIER_PARNAME, this::mapDistributorSupplier,
                        nq + "%", nq + "%", lim);
                if (supplier.size() < 5) {
                    List<SuggestItemResponse> contains = jdbcTemplate.query(
                            SQL_SUPPLIER_PARNAME, this::mapDistributorSupplier,
                            "%" + nq + "%", "%" + nq + "%", lim);
                    supplier = mergeDistinct(supplier, contains, lim);
                }
                history = mergeDistinct(history, supplier, lim);
            }
            if (!nq.isEmpty() && history.size() < 3) {
                List<SuggestItemResponse> master = jdbcTemplate.query(
                        SQL_SUPPLIER_MASTER, this::mapSupplierMaster,
                        nq + "%", nq + "%", lim);
                if (master.size() < 3) {
                    List<SuggestItemResponse> contains = jdbcTemplate.query(
                            SQL_SUPPLIER_MASTER, this::mapSupplierMaster,
                            "%" + nq + "%", "%" + nq + "%", lim);
                    master = mergeDistinct(master, contains, lim);
                }
                history = mergeDistinct(history, master, lim);
            }
            return history;
        });
    }

    private SuggestItemResponse mapDistributor(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp terakhir = rs.getTimestamp("terakhir");
        String nama = rs.getString("nama");
        return SuggestItemResponse.builder()
                .nama(nama)
                .normalized(normalizeDistributor(nama))
                .sumber("KLAIM")
                .jumlah(rs.getLong("jumlah"))
                .terakhir(terakhir == null ? null : terakhir.toLocalDateTime())
                .build();
    }

    private static String normalizeDistributor(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toUpperCase()
                .replaceAll("[%_]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ---------------------------------------------------------------------
    // Refresh master
    // ---------------------------------------------------------------------

    /** Seed master saat startup hanya bila tabel masih kosong. */
    public void seedIfEmpty() {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM master_barang", Long.class);
            if (count != null && count > 0) {
                return;
            }
            log.info("master_barang kosong — mulai seed dari 7 sumber...");
            refreshMaster();
        } catch (Exception e) {
            log.warn("Gagal seed master_barang saat startup: {}", e.getMessage());
        }
    }

    /** Refresh penuh harian (02:00 WIB). */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Jakarta")
    public void refreshDaily() {
        try {
            refreshMaster();
            refreshDistributorAliases();
        } catch (Exception e) {
            log.error("Gagal refresh master servis: {}", e.getMessage());
        }
    }

    public void refreshMaster() {
        long start = System.currentTimeMillis();
        int affected = jdbcTemplate.update(SQL_SEED_MASTER);
        log.info("master_barang refresh selesai: {} baris dalam {} ms",
                affected, System.currentTimeMillis() - start);
    }

    public void refreshDistributorAliases() {
        long start = System.currentTimeMillis();
        jdbcTemplate.update("""
                UPDATE distributor
                SET normalized_name = movva_normalize_item(nama_distributor)
                WHERE nama_distributor IS NOT NULL AND btrim(nama_distributor) <> ''
                """);
        jdbcTemplate.update("""
                DELETE FROM distributor_alias a
                WHERE NOT EXISTS (SELECT 1 FROM distributor d WHERE d.id = a.distributor_id)
                """);
        int inserted = jdbcTemplate.update(SQL_ALIAS_SUPPLIER);
        log.info("distributor_alias refresh selesai: {} baris baru dalam {} ms",
                inserted, System.currentTimeMillis() - start);
    }

    // ---------------------------------------------------------------------
    // Util
    // ---------------------------------------------------------------------

    private static int clampLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private List<SuggestItemResponse> mergeDistinct(List<SuggestItemResponse> first,
                                                    List<SuggestItemResponse> second,
                                                    int limit) {
        LinkedHashMap<String, SuggestItemResponse> merged = new LinkedHashMap<>();
        for (SuggestItemResponse item : first) {
            merged.putIfAbsent(key(item), item);
        }
        for (SuggestItemResponse item : second) {
            merged.putIfAbsent(key(item), item);
        }
        List<SuggestItemResponse> result = new ArrayList<>(merged.values());
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    private static String key(SuggestItemResponse item) {
        if (item.getNormalized() != null && !item.getNormalized().isBlank()) {
            return item.getNormalized();
        }
        return NormalizationUtil.normalizeItemName(item.getNama());
    }

    private List<SuggestItemResponse> cached(String key, Supplier<List<SuggestItemResponse>> loader) {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.expiresAt() > now) {
            return entry.data();
        }
        List<SuggestItemResponse> data = loader.get();
        if (data == null) {
            data = List.of();
        } else {
            data = List.copyOf(data);
        }
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new CacheEntry(data, now + CACHE_TTL_MS));
        return data;
    }

    private record CacheEntry(List<SuggestItemResponse> data, long expiresAt) {
    }
}
