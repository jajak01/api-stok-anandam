package com.stok.anandam.store.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(PostgresSchemaInitializer.class);

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate pgJdbcTemplate;

    @PostConstruct
    public void init() {
        log.info("=== STARTING POSTGRESQL DATABASE SCHEMA INITIALIZATION ===");
        fixUserRoleConstraint();
        addRevisionFields();
        fixMemoStatuses();
        addSalesProfitabilityColumns();
        addPricelistNormalizedColumn();
        initUserPermissionsTable();
        initServisV3Schema();
        initSearchPerformanceIndexes();
        log.info("=== POSTGRESQL DATABASE SCHEMA INITIALIZATION COMPLETED ===");
    }

    /**
     * Index pendukung pencarian (P0 — 8 Nov 2026).
     *
     * Semua query list memakai pola {@code LOWER(kolom) LIKE '%kata%'} sehingga
     * hanya index trigram (pg_trgm) yang bisa dipakai; urutan tanggal juga butuh
     * B-Tree. Dibuat dengan CONCURRENTLY supaya tidak mengunci tabel saat
     * aplikasi start. Idempotent: index yang sudah ada dilewati.
     *
     * Catatan: pembuatan pertama pada tabel besar bisa memakan beberapa menit.
     * Jika pg_trgm belum tersedia, index trigram dilewati (search tetap jalan,
     * hanya memakai sequential scan).
     */
    private void initSearchPerformanceIndexes() {
        log.info("Checking search performance indexes...");
        boolean trigram = true;
        try {
            pgJdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        } catch (Exception e) {
            trigram = false;
            log.warn("pg_trgm tidak tersedia; index trigram dilewati: {}", e.getMessage());
        }

        java.util.List<String> ddl = new java.util.ArrayList<>();
        // ================= PENJUALAN =================
        ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales_doc_date ON sales (doc_date DESC)");
        ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales_emp_date ON sales (emp_code, doc_date DESC)");
        ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales_dep_date ON sales (dep_code, doc_date DESC)");
        // ================= PEMBELIAN =================
        ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_purchases_doc_date ON purchases (doc_date DESC)");
        ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_purchases_docno_btrim ON purchases (btrim(doc_no_p))");
        // ================= ITEM SN =================
        ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_isn_type_date ON item_serial_numbers (upper(type), tanggal DESC)");
        ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_isn_sn_norm ON item_serial_numbers (upper(btrim(sn)))");
        // ================= DATA WAREHOUSE =================
        ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_old_sales_doc_date ON old_sales (doc_date DESC)");
        ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_old_purchase_doc_date ON old_purchase (doc_date DESC)");
        ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_old_item_sn_date ON old_item_serial_numbers (tanggal DESC)");
        // P1: exact-match verifikasi SN (BL lama) — ekspresi sama dengan query.
        ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_old_item_sn_sn_norm ON old_item_serial_numbers (upper(btrim(sn)))");

        if (trigram) {
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales_docno_trgm ON sales USING gin (lower(doc_no) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales_parname_trgm ON sales USING gin (lower(par_name) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales_itemname_trgm ON sales USING gin (lower(item_name) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_purchases_docno_trgm ON purchases USING gin (lower(doc_no_p) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_purchases_parname_trgm2 ON purchases USING gin (lower(par_name) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_purchases_itemname_trgm ON purchases USING gin (lower(item_name) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_isn_sn_trgm ON item_serial_numbers USING gin (lower(sn) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_isn_docid_trgm ON item_serial_numbers USING gin (lower(doc_id) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_isn_itemname_trgm ON item_serial_numbers USING gin (lower(item_name) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_old_sales_search_trgm ON old_sales USING gin (" +
                    "lower(coalesce(doc_no,'') || ' ' || coalesce(par_name,'') || ' ' || coalesce(item_name,'')) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_old_purchase_search_trgm ON old_purchase USING gin (" +
                    "lower(coalesce(doc_no_p,'') || ' ' || coalesce(par_name,'') || ' ' || coalesce(item_name,'')) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_old_item_sn_sn_trgm ON old_item_serial_numbers USING gin (lower(sn) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_old_item_sn_docid_trgm ON old_item_serial_numbers USING gin (lower(doc_id) gin_trgm_ops)");
            ddl.add("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_old_item_sn_itemname_trgm ON old_item_serial_numbers USING gin (lower(item_name) gin_trgm_ops)");
        }

        for (String sql : ddl) {
            try {
                pgJdbcTemplate.execute(sql);
            } catch (Exception e) {
                // Jangan gagalkan startup hanya karena satu index (mis. tabel belum ada).
                log.warn("Gagal membuat index: {} ({})", sql, e.getMessage());
            }
        }
        log.info("Search performance indexes checked ({} statements).", ddl.size());
    }

    private void addPricelistNormalizedColumn() {
        log.info("Checking and adding normalized_item_name column to pricelist table...");
        try {
            pgJdbcTemplate.execute("ALTER TABLE pricelist ADD COLUMN IF NOT EXISTS normalized_item_name VARCHAR(500)");
            pgJdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_pricelist_norm_item_name ON pricelist (normalized_item_name)");
            log.info("Successfully checked/added pricelist.normalized_item_name.");
        } catch (Exception e) {
            log.error("Failed to add pricelist.normalized_item_name: {}", e.getMessage());
        }
    }

    private void fixUserRoleConstraint() {
        log.info("Checking and fixing users_role_check constraint...");
        try {
            String sqlDrop = "ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check";
            String sqlAdd = "ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('MANAGER', 'ADMIN', 'SPV_MARKETING', 'SPV_GUDANG', 'SPV_TEKNISI', 'MARKETING', 'MARKETING_TOKO', 'MARKETING_PROJECT', 'MARKETING_DISTRIBUSI', 'MARKETING_ONLINE', 'GUDANG', 'NOTA', 'DELIVERY', 'TEKNISI'))";
            
            pgJdbcTemplate.execute(sqlDrop);
            pgJdbcTemplate.execute(sqlAdd);
            log.info("Successfully updated users_role_check constraint.");
        } catch (Exception e) {
            log.error("Failed to update users_role_check constraint: {}", e.getMessage());
        }
    }

    private void addRevisionFields() {
        log.info("Checking and adding revision fields to memos table...");
        try {
            pgJdbcTemplate.execute("ALTER TABLE memos ADD COLUMN IF NOT EXISTS revised_from_id UUID");
            pgJdbcTemplate.execute("ALTER TABLE memos ADD COLUMN IF NOT EXISTS revision_to_id UUID");
            log.info("Successfully checked/added revision fields.");
        } catch (Exception e) {
            log.error("Failed to add revision fields: {}", e.getMessage());
        }
    }

    private void addSalesProfitabilityColumns() {
        log.info("Checking and adding sales profitability columns (hpp_satuan, total_hpp, laba_kotor)...");
        try {
            pgJdbcTemplate.execute("ALTER TABLE sales ADD COLUMN IF NOT EXISTS hpp_satuan NUMERIC(19,6)");
            pgJdbcTemplate.execute("ALTER TABLE sales ADD COLUMN IF NOT EXISTS total_hpp NUMERIC(19,6)");
            pgJdbcTemplate.execute("ALTER TABLE sales ADD COLUMN IF NOT EXISTS laba_kotor NUMERIC(19,6)");
            log.info("Successfully checked/added sales profitability columns.");
        } catch (Exception e) {
            log.error("Failed to add sales profitability columns: {}", e.getMessage());
        }
    }

    private void fixMemoStatuses() {
        log.info("Starting legacy MemoStatus migration in database...");
        try {
            // Mappings for 'memos' table
            pgJdbcTemplate.execute("UPDATE memos SET status_akhir = 'MENUNGGU_PERSETUJUAN' WHERE status_akhir = 'PENDING_MENUNGGU'");
            pgJdbcTemplate.execute("UPDATE memos SET status_akhir = 'DISETUJUI' WHERE status_akhir = 'PENDING_DISETUJUI'");
            pgJdbcTemplate.execute("UPDATE memos SET status_akhir = 'DITOLAK' WHERE status_akhir = 'PENDING_DITOLAK'");
            pgJdbcTemplate.execute("UPDATE memos SET status_akhir = 'SIAP_PENUGASAN' WHERE status_akhir = 'READY_DI_GUDANG'");
            pgJdbcTemplate.execute("UPDATE memos SET status_akhir = 'PROSES_GUDANG' WHERE status_akhir = 'PERSIAPAN_GUDANG'");
            pgJdbcTemplate.execute("UPDATE memos SET status_akhir = 'SIAP_PENUGASAN' WHERE status_akhir = 'VERIFIKASI_AKHIR_GUDANG'");
            pgJdbcTemplate.execute("UPDATE memos SET status_akhir = 'DITERIMA_USER' WHERE status_akhir = 'SUDAH_DIKIRIM'");
            pgJdbcTemplate.execute("UPDATE memos SET status_akhir = 'TERKIRIM_SEBAGIAN' WHERE status_akhir = 'PARTIAL_DELIVERED'");
            pgJdbcTemplate.execute("UPDATE memos SET status_akhir = 'TERKIRIM_SEBAGIAN' WHERE status_akhir = 'DIKIRIM_SEBAGIAN'");
            pgJdbcTemplate.execute("UPDATE memos SET status_akhir = 'DALAM_PENGIRIMAN' WHERE status_akhir = 'PROSES_PENGIRIMAN'");
            
            // Mappings for 'memo_logs' table (optional but good for consistency)
            pgJdbcTemplate.execute("UPDATE memo_logs SET status = 'MENUNGGU_PERSETUJUAN' WHERE status = 'PENDING_MENUNGGU'");
            pgJdbcTemplate.execute("UPDATE memo_logs SET status = 'DISETUJUI' WHERE status = 'PENDING_DISETUJUI'");
            pgJdbcTemplate.execute("UPDATE memo_logs SET status = 'DITOLAK' WHERE status = 'PENDING_DITOLAK'");
            pgJdbcTemplate.execute("UPDATE memo_logs SET status = 'SIAP_PENUGASAN' WHERE status = 'READY_DI_GUDANG'");
            pgJdbcTemplate.execute("UPDATE memo_logs SET status = 'PROSES_GUDANG' WHERE status = 'PERSIAPAN_GUDANG'");
            pgJdbcTemplate.execute("UPDATE memo_logs SET status = 'DITERIMA_USER' WHERE status = 'SUDAH_DIKIRIM'");
            pgJdbcTemplate.execute("UPDATE memo_logs SET status = 'TERKIRIM_SEBAGIAN' WHERE status = 'PARTIAL_DELIVERED'");
            pgJdbcTemplate.execute("UPDATE memo_logs SET status = 'TERKIRIM_SEBAGIAN' WHERE status = 'DIKIRIM_SEBAGIAN'");
            pgJdbcTemplate.execute("UPDATE memo_logs SET status = 'DALAM_PENGIRIMAN' WHERE status = 'PROSES_PENGIRIMAN'");
            log.info("Successfully completed legacy MemoStatus migration.");
        } catch (Exception e) {
            log.error("Failed to migrate legacy MemoStatus: {}", e.getMessage());
        }
    }

    /**
     * Skema aditif v3 (Servis): kolom baru, master barang, alias supplier,
     * log posisi, dan fungsi normalisasi. Idempotent; tidak melakukan backfill
     * berat saat startup (backfill dijalankan terpisah di F1).
     */
    private void initServisV3Schema() {
        log.info("Checking servis v3 schema...");
        try {
            pgJdbcTemplate.execute(
                "ALTER TABLE transaksi_servis " +
                "ADD COLUMN IF NOT EXISTS nama_barang VARCHAR(500), " +
                "ADD COLUMN IF NOT EXISTS nama_barang_norm VARCHAR(500), " +
                "ADD COLUMN IF NOT EXISTS supplier_pembelian VARCHAR(200), " +
                "ADD COLUMN IF NOT EXISTS supplier_pembelian_norm VARCHAR(200), " +
                "ADD COLUMN IF NOT EXISTS nama_distributor VARCHAR(200), " +
                "ADD COLUMN IF NOT EXISTS nama_distributor_norm VARCHAR(200), " +
                "ADD COLUMN IF NOT EXISTS no_nota_supplier VARCHAR(100), " +
                "ADD COLUMN IF NOT EXISTS posisi_barang VARCHAR(30), " +
                "ADD COLUMN IF NOT EXISTS posisi_sumber VARCHAR(10) DEFAULT 'AUTO', " +
                "ADD COLUMN IF NOT EXISTS posisi_changed_at TIMESTAMPTZ, " +
                "ADD COLUMN IF NOT EXISTS posisi_changed_by UUID, " +
                "ADD COLUMN IF NOT EXISTS sn_verified BOOLEAN DEFAULT false, " +
                "ADD COLUMN IF NOT EXISTS sn_verified_at TIMESTAMPTZ"
            );

            pgJdbcTemplate.execute(
                "ALTER TABLE distributor " +
                "ADD COLUMN IF NOT EXISTS normalized_name VARCHAR(200), " +
                "ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true"
            );

            pgJdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS master_barang (" +
                "  id BIGSERIAL PRIMARY KEY, " +
                "  nama_tampil VARCHAR(500) NOT NULL, " +
                "  normalized_name VARCHAR(500) NOT NULL UNIQUE, " +
                "  sumber VARCHAR(20), " +
                "  jumlah_pakai BIGINT DEFAULT 0, " +
                "  last_seen_at TIMESTAMPTZ, " +
                "  is_manual BOOLEAN DEFAULT false, " +
                "  is_active BOOLEAN DEFAULT true" +
                ")"
            );

            // Tanpa FK ke distributor: tabel distributor di-TRUNCATE oleh sync CSV.
            pgJdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS distributor_alias (" +
                "  id BIGSERIAL PRIMARY KEY, " +
                "  distributor_id BIGINT NOT NULL, " +
                "  alias VARCHAR(200) NOT NULL, " +
                "  alias_norm VARCHAR(200) NOT NULL UNIQUE, " +
                "  created_at TIMESTAMPTZ DEFAULT now()" +
                ")"
            );

            pgJdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS servis_posisi_log (" +
                "  id BIGSERIAL PRIMARY KEY, " +
                "  transaksi_id UUID NOT NULL REFERENCES transaksi_servis(id), " +
                "  posisi_lama VARCHAR(30), " +
                "  posisi_baru VARCHAR(30) NOT NULL, " +
                "  sumber VARCHAR(10) NOT NULL, " +
                "  alasan TEXT, " +
                "  changed_by BIGINT, " +
                "  created_at TIMESTAMPTZ DEFAULT now()" +
                ")"
            );

            // users.id is BIGINT, not UUID. Migrate the early v3 columns if they
            // were created as UUID so the actor id can be stored consistently.
            pgJdbcTemplate.execute(
                "DO $$ BEGIN " +
                "IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='transaksi_servis' " +
                "AND column_name='posisi_changed_by' AND data_type='uuid') THEN " +
                "ALTER TABLE transaksi_servis ALTER COLUMN posisi_changed_by TYPE BIGINT USING NULL; END IF; " +
                "END $$;"
            );
            pgJdbcTemplate.execute(
                "DO $$ BEGIN " +
                "IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='servis_posisi_log' " +
                "AND column_name='changed_by' AND data_type='uuid') THEN " +
                "ALTER TABLE servis_posisi_log ALTER COLUMN changed_by TYPE BIGINT USING NULL; END IF; " +
                "END $$;"
            );

            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_master_barang_prefix ON master_barang (normalized_name text_pattern_ops)");
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_master_barang_seen ON master_barang (last_seen_at DESC)");
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_distributor_alias_norm ON distributor_alias (alias_norm)");
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_posisi_log_transaksi ON servis_posisi_log (transaksi_id, created_at DESC)");
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_posisi_log_posisi ON servis_posisi_log (posisi_baru, created_at)");
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_servis_nama_barang_norm ON transaksi_servis (nama_barang_norm text_pattern_ops)");
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_servis_supplier_norm ON transaksi_servis (supplier_pembelian_norm text_pattern_ops)");
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_servis_distributor_norm ON transaksi_servis (nama_distributor_norm)");
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_servis_posisi ON transaksi_servis (posisi_barang)");

            pgJdbcTemplate.execute(
                "CREATE OR REPLACE FUNCTION movva_normalize_item(txt text) " +
                "RETURNS text LANGUAGE sql IMMUTABLE AS $$ " +
                "  SELECT btrim(regexp_replace(" +
                "           regexp_replace(" +
                "             replace(replace(upper(coalesce(txt,'')), chr(8482), ''), chr(174), ''), " +
                "             '[^A-Z0-9 ]', ' ', 'g'), " +
                "         '\\s+', ' ', 'g')); " +
                "$$"
            );

            // Expression index untuk saran supplier per nama barang (query < 20 ms).
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_purchases_norm_item ON purchases (movva_normalize_item(item_name))");
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_old_purchase_norm_item ON old_purchase (movva_normalize_item(item_name))");
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_purchases_parname_upper ON purchases (upper(btrim(par_name)) text_pattern_ops)");
            pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_old_purchase_parname_upper ON old_purchase (upper(btrim(par_name)) text_pattern_ops)");

            // Index trigram untuk fallback contains pada saran supplier.
            try {
                pgJdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
                pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_purchases_parname_trgm ON purchases USING gin (upper(btrim(par_name)) gin_trgm_ops)");
                pgJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_old_purchase_parname_trgm ON old_purchase USING gin (upper(btrim(par_name)) gin_trgm_ops)");
            } catch (Exception e) {
                log.warn("pg_trgm tidak tersedia; fallback contains memakai seq scan: {}", e.getMessage());
            }

            log.info("Successfully checked/added servis v3 schema.");
        } catch (Exception e) {
            log.error("Failed to initialize servis v3 schema: {}", e.getMessage());
        }
    }

    private void initUserPermissionsTable() {
        log.info("Checking and creating user_permissions table if not exists...");
        try {
            pgJdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS user_permissions (" +
                "    user_id BIGINT NOT NULL, " +
                "    permission VARCHAR(100) NOT NULL, " +
                "    PRIMARY KEY (user_id, permission), " +
                "    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ")"
            );
            log.info("Successfully verified user_permissions table.");
        } catch (Exception e) {
            log.error("Failed to initialize user_permissions table: {}", e.getMessage());
        }
    }
}
