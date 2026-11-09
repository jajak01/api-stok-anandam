package com.stok.anandam.store.dto;

import com.stok.anandam.store.core.postgres.model.StatusPembayaran;
import com.stok.anandam.store.core.postgres.model.StatusServis;
import com.stok.anandam.store.core.postgres.model.TransaksiServis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransaksiServisResponse {

    private UUID id;
    private String noServis;
    private UUID trackingToken;
    
    // Pelanggan Info
    private UUID pelangganId;
    private String namaPelanggan;
    private String noTelepon;
    
    // Barang Info
    private String jenisBarang;
    private String merek;
    private String namaBarang;
    private String supplierPembelian;
    private String namaDistributor;
    private String noNotaSupplier;
    private String posisiBarang;
    private String posisiSumber;
    private LocalDateTime posisiChangedAt;
    private Boolean snVerified;
    private LocalDateTime snVerifiedAt;
    private String modelSeri;
    private String modelSeriLama;
    private String kelengkapan;
    private String kerusakan;
    
    // Servis Detail
    private BigDecimal dp;
    private BigDecimal estimasiBiaya;
    private StatusServis statusTerkini;
    private String kondisiServis;
    private String ketTindakan;
    
    // Staff Info
    private String namaPenerima;
    private String namaTeknisi;
    private String namaPenyerah;
    
    // Status Waktu
    private LocalDateTime tglTerima;
    private LocalDateTime tglDitangani;
    private LocalDateTime tglAmbil;
    
    // Pengambilan & Finalisasi
    private String pengambilNama;
    private String durasiGaransi;
    private LocalDateTime tglBatasGaransi;
    private Long sisaHariGaransi;
    private BigDecimal biayaFinal;
    private BigDecimal modalSparepart;
    private StatusPembayaran statusBayar;
    private LocalDate tglJatuhTempo;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Jumlah hari sejak barang dikirim ke distributor.
     * null jika bukan status KLAIM atau belum dikirim.
     * Untuk status KLAIM_SUDAH_DIAMBIL: selisih tanggalKembali - tanggalKirim (fixed).
     * Untuk status KLAIM_DIKIRIM / KLAIM_SUDAH_DIKIRIM: selisih now - tanggalKirim (berjalan).
     */
    private Long hariDiDistributor;

    /**
     * Menghitung sisa hari garansi secara real-time.
     * Jika tglBatasGaransi null atau sudah lewat, mengembalikan 0.
     */
    private static Long calculateSisaHariGaransi(LocalDateTime tglBatasGaransi) {
        if (tglBatasGaransi == null) return 0L;
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(tglBatasGaransi)) return 0L;
        return ChronoUnit.DAYS.between(now, tglBatasGaransi);
    }

    /**
     * Menghitung hari di distributor dari data KlaimDistributor.
     * DIPANGGIL DARI SERVICE LAYER (setelah data di-fetch).
     */
    public static void enrichHariDiDistributor(List<TransaksiServisResponse> responses,
                                                java.util.Map<UUID, com.stok.anandam.store.core.postgres.model.KlaimDistributor> klaimMap) {
        LocalDateTime now = LocalDateTime.now();
        for (TransaksiServisResponse r : responses) {
            if (r.getId() == null) continue;
            var klaim = klaimMap.get(r.getId());
            if (klaim == null || klaim.getTanggalKirim() == null) {
                r.hariDiDistributor = null;
                continue;
            }
            // Jika sudah diambil, gunakan tanggalKembali (hari fixed)
            if (klaim.getTanggalKembali() != null) {
                r.hariDiDistributor = ChronoUnit.DAYS.between(klaim.getTanggalKirim(), klaim.getTanggalKembali());
            } else {
                // Masih dalam proses — hitung dari sekarang
                r.hariDiDistributor = ChronoUnit.DAYS.between(klaim.getTanggalKirim(), now);
            }
            if (r.hariDiDistributor < 0) r.hariDiDistributor = 0L;
        }
    }

    public static TransaksiServisResponse fromEntity(TransaksiServis transaksi) {
        if (transaksi == null) {
            return null;
        }
        
        return TransaksiServisResponse.builder()
                .id(transaksi.getId())
                .noServis(transaksi.getNoServis())
                .trackingToken(transaksi.getTrackingToken())
                
                .pelangganId(transaksi.getPelanggan() != null ? transaksi.getPelanggan().getId() : null)
                .namaPelanggan(transaksi.getPelanggan() != null ? transaksi.getPelanggan().getNamaPelanggan() : null)
                .noTelepon(transaksi.getPelanggan() != null ? transaksi.getPelanggan().getNoTelepon() : null)
                
                .jenisBarang(transaksi.getJenisBarang())
                .merek(transaksi.getMerek())
                .namaBarang(transaksi.getNamaBarang())
                .supplierPembelian(transaksi.getSupplierPembelian())
                .namaDistributor(transaksi.getNamaDistributor())
                .noNotaSupplier(transaksi.getNoNotaSupplier())
                .posisiBarang(transaksi.getPosisiBarang())
                .posisiSumber(transaksi.getPosisiSumber())
                .posisiChangedAt(transaksi.getPosisiChangedAt())
                .snVerified(Boolean.TRUE.equals(transaksi.getSnVerified()))
                .snVerifiedAt(transaksi.getSnVerifiedAt())
                .modelSeri(transaksi.getModelSeri())
                .modelSeriLama(transaksi.getModelSeriLama())
                .kelengkapan(transaksi.getKelengkapan())
                .kerusakan(transaksi.getKerusakan())
                
                .dp(transaksi.getDp())
                .estimasiBiaya(transaksi.getEstimasiBiaya())
                .statusTerkini(transaksi.getStatusTerkini())
                .kondisiServis(transaksi.getKondisiServis())
                .ketTindakan(transaksi.getKetTindakan())
                
                .namaPenerima(transaksi.getPenerima() != null ? transaksi.getPenerima().getNama() : null)
                .namaTeknisi(transaksi.getTeknisi() != null ? transaksi.getTeknisi().getNama() : null)
                .namaPenyerah(transaksi.getPenyerah() != null ? transaksi.getPenyerah().getNama() : null)
                
                .tglTerima(transaksi.getTglTerima())
                .tglDitangani(transaksi.getTglDitangani())
                .tglAmbil(transaksi.getTglAmbil())
                
                .pengambilNama(transaksi.getPengambilNama())
                .durasiGaransi(transaksi.getDurasiGaransi())
                .tglBatasGaransi(transaksi.getTglBatasGaransi())
                .sisaHariGaransi(calculateSisaHariGaransi(transaksi.getTglBatasGaransi()))
                .biayaFinal(transaksi.getBiayaFinal())
                .modalSparepart(transaksi.getModalSparepart())
                .statusBayar(transaksi.getStatusBayar())
                .tglJatuhTempo(transaksi.getTglJatuhTempo())
                
                .createdAt(transaksi.getCreatedAt())
                .updatedAt(transaksi.getUpdatedAt())
                .build();
    }
}
