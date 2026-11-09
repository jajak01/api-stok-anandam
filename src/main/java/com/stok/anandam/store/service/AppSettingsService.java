package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.AppSetting;
import com.stok.anandam.store.core.postgres.repository.AppSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Setting aplikasi key-value. Khususnya:
 * HARGA_BUTTOM_PERSEN = persentase tambahan untuk menghitung hargaButtom
 * (harga jual dasar untuk marketing) dari Modal Final (pricelist.modal):
 *   hargaButtom = Modal Final + (Modal Final * percent / 100)
 *
 * Fallback ke hargaHpp hanya bila Modal Final tidak tersedia. Default 3 (%)
 * bila belum pernah disimpan. Nilai bisa diubah kapan saja via
 * PUT /api/v1/stocks/harga-buttom-percent (ADMIN/MANAGER).
 */
@Service
public class AppSettingsService {

    public static final String HARGA_BUTTOM_PERSEN_KEY = "HARGA_BUTTOM_PERSEN";
    private static final BigDecimal DEFAULT_PERCENT = new BigDecimal("3");

    @Autowired
    private AppSettingRepository appSettingRepository;

    /** Ambil persen hargaButtom (default 3). */
    public BigDecimal getHargaButtomPercent() {
        return appSettingRepository.findById(HARGA_BUTTOM_PERSEN_KEY)
                .map(s -> safePercent(s.getValue()))
                .orElse(DEFAULT_PERCENT);
    }

    /** Simpan persen hargaButtom (0..1000). */
    public BigDecimal setHargaButtomPercent(BigDecimal percent) {
        if (percent == null) {
            throw new IllegalArgumentException("percent wajib diisi");
        }
        BigDecimal value = percent.setScale(0, RoundingMode.HALF_UP);
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("1000")) > 0) {
            throw new IllegalArgumentException("percent harus antara 0 s/d 1000");
        }
        appSettingRepository.save(AppSetting.builder()
                .key(HARGA_BUTTOM_PERSEN_KEY)
                .value(value.toPlainString())
                .build());
        return value;
    }

    private BigDecimal safePercent(String raw) {
        try {
            BigDecimal v = new BigDecimal(raw.trim());
            if (v.compareTo(BigDecimal.ZERO) < 0) return DEFAULT_PERCENT;
            return v;
        } catch (Exception e) {
            return DEFAULT_PERCENT;
        }
    }
}
