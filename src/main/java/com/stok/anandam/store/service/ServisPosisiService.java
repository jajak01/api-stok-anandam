package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.StatusServis;
import com.stok.anandam.store.core.postgres.model.TransaksiServis;
import com.stok.anandam.store.util.NormalizationUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Resolves the derived physical location of a service item. */
@Service
public class ServisPosisiService {
    /**
     * Recalculate the derived position. A MANUAL override is preserved until the
     * service/claim status changes; status-changing flows call
     * {@link #clearManualOverride(TransaksiServis)} first.
     */
    public boolean resolve(TransaksiServis transaksi) {
        if (isManual(transaksi)) {
            return false;
        }
        String next = resolveValue(transaksi);
        boolean changed = !next.equals(transaksi.getPosisiBarang());
        transaksi.setPosisiBarang(next);
        transaksi.setPosisiSumber("AUTO");
        if (changed || transaksi.getPosisiChangedAt() == null) transaksi.setPosisiChangedAt(LocalDateTime.now());
        return changed;
    }

    /** Called whenever the status changes so the next resolve is automatic again. */
    public void clearManualOverride(TransaksiServis transaksi) {
        if (isManual(transaksi)) {
            transaksi.setPosisiSumber("AUTO");
        }
    }

    private boolean isManual(TransaksiServis transaksi) {
        return "MANUAL".equalsIgnoreCase(transaksi.getPosisiSumber());
    }

    String resolveValue(TransaksiServis transaksi) {
        StatusServis status = transaksi.getStatusTerkini();
        String customer = transaksi.getPelanggan() == null ? "" :
                NormalizationUtil.normalizeItemName(transaksi.getPelanggan().getNamaPelanggan());
        if (status == StatusServis.SUDAH_DIAMBIL &&
                ("STOK DI GUDANG RETURN".equals(customer) || "STOCK GUDANG RETURN".equals(customer))) return "GUDANG_PUSAT";
        if (status == StatusServis.KLAIM_DIKIRIM || status == StatusServis.KLAIM_SUDAH_DIKIRIM) return "SUPPLIER";
        if (status == StatusServis.BATAL || status == StatusServis.SUDAH_DIAMBIL) return "KELUAR";
        return "GUDANG_SERVIS";
    }
}
