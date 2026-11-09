package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.PelangganServis;
import com.stok.anandam.store.core.postgres.model.StatusServis;
import com.stok.anandam.store.core.postgres.model.TransaksiServis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServisPosisiServiceTest {
    private final ServisPosisiService service = new ServisPosisiService();

    @Test
    void resolvesAllOperationalPositions() {
        assertPosisi(StatusServis.BELUM_CEK, "Pelanggan Biasa", "GUDANG_SERVIS");
        assertPosisi(StatusServis.KLAIM_MENUNGGU_PENGIRIMAN, "Pelanggan Biasa", "GUDANG_SERVIS");
        assertPosisi(StatusServis.KLAIM_DIKIRIM, "Pelanggan Biasa", "SUPPLIER");
        assertPosisi(StatusServis.KLAIM_SUDAH_DIKIRIM, "Pelanggan Biasa", "SUPPLIER");
        assertPosisi(StatusServis.KLAIM_SUDAH_DIAMBIL, "Pelanggan Biasa", "GUDANG_SERVIS");
        assertPosisi(StatusServis.SUDAH_DIAMBIL, "Pelanggan Biasa", "KELUAR");
        assertPosisi(StatusServis.BATAL, "Pelanggan Biasa", "KELUAR");
    }

    @Test
    void returnWarehouseCustomerBecomesGudangPusatWhenCollected() {
        assertPosisi(StatusServis.SUDAH_DIAMBIL, "STOK DI GUDANG RETURN", "GUDANG_PUSAT");
        assertPosisi(StatusServis.SUDAH_DIAMBIL, "stock gudang return", "GUDANG_PUSAT");
    }

    @Test
    void manualOverrideIsPreservedUntilStatusChange() {
        PelangganServis customer = new PelangganServis();
        customer.setNamaPelanggan("Pelanggan Biasa");
        TransaksiServis transaksi = TransaksiServis.builder()
                .pelanggan(customer)
                .statusTerkini(StatusServis.KLAIM_DIKIRIM)
                .posisiBarang("GUDANG_SERVIS")
                .posisiSumber("MANUAL")
                .build();

        // Override manual dipertahankan selama status belum berubah.
        service.resolve(transaksi);
        assertEquals("GUDANG_SERVIS", transaksi.getPosisiBarang());
        assertEquals("MANUAL", transaksi.getPosisiSumber());

        // Perubahan status menghapus override, lalu resolver menghitung ulang.
        service.clearManualOverride(transaksi);
        service.resolve(transaksi);
        assertEquals("SUPPLIER", transaksi.getPosisiBarang());
        assertEquals("AUTO", transaksi.getPosisiSumber());
    }

    private void assertPosisi(StatusServis status, String customerName, String expected) {
        PelangganServis customer = new PelangganServis();
        customer.setNamaPelanggan(customerName);
        TransaksiServis transaksi = TransaksiServis.builder().pelanggan(customer).statusTerkini(status).build();
        service.resolve(transaksi);
        assertEquals(expected, transaksi.getPosisiBarang());
        assertEquals("AUTO", transaksi.getPosisiSumber());
    }
}
