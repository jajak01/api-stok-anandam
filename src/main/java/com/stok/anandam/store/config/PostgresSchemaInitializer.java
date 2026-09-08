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
        log.info("=== POSTGRESQL DATABASE SCHEMA INITIALIZATION COMPLETED ===");
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
