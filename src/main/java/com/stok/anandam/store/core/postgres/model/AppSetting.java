package com.stok.anandam.store.core.postgres.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Key-value setting aplikasi (disimpan di tabel app_settings).
 * Hibernate hbm2ddl=update akan membuat tabel ini otomatis saat startup.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "app_settings")
public class AppSetting {

    /** Nama key setting, contoh: HARGA_BUTTOM_PERSEN */
    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", length = 500)
    private String value;
}
