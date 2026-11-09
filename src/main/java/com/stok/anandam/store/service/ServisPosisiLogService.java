package com.stok.anandam.store.service;

import com.stok.anandam.store.dto.ServisPosisiLogResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ServisPosisiLogService {
    private final JdbcTemplate jdbcTemplate;

    public ServisPosisiLogService(@Qualifier("pgJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(UUID transaksiId, String lama, String baru, String sumber, String alasan) {
        jdbcTemplate.update("INSERT INTO servis_posisi_log (transaksi_id, posisi_lama, posisi_baru, sumber, alasan) VALUES (?, ?, ?, ?, ?)",
                transaksiId, lama, baru, sumber, alasan);
    }

    public void write(UUID transaksiId, String lama, String baru, String sumber, String alasan, Long changedBy) {
        jdbcTemplate.update("INSERT INTO servis_posisi_log (transaksi_id, posisi_lama, posisi_baru, sumber, alasan, changed_by) VALUES (?, ?, ?, ?, ?, ?)",
                transaksiId, lama, baru, sumber, alasan, changedBy);
    }

    public List<ServisPosisiLogResponse> findByTransaksiId(UUID transaksiId) {
        String sql = """
                SELECT l.id, l.posisi_lama, l.posisi_baru, l.sumber, l.alasan,
                       l.created_at, u.nama AS changed_by
                FROM servis_posisi_log l
                LEFT JOIN users u ON u.id = l.changed_by
                WHERE l.transaksi_id = ?
                ORDER BY l.created_at DESC, l.id DESC
                """;
        return jdbcTemplate.query(sql, (rs, row) -> ServisPosisiLogResponse.builder()
                .id(rs.getLong("id"))
                .posisiLama(rs.getString("posisi_lama"))
                .posisiBaru(rs.getString("posisi_baru"))
                .sumber(rs.getString("sumber"))
                .alasan(rs.getString("alasan"))
                .changedBy(rs.getString("changed_by"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .build(), transaksiId);
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        return jdbcTemplate.query(sql, mapper, args);
    }
}
