package com.stok.anandam.store.core.postgres.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transaksi_servis")
public class TransaksiServis {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "no_servis", nullable = false, unique = true)
    private String noServis;

    @Column(name = "tracking_token", unique = true)
    private UUID trackingToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pelanggan_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private PelangganServis pelanggan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "penerima_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User penerima;

    @Column(name = "tgl_terima")
    private LocalDateTime tglTerima;

    @Column(name = "jenis_barang", nullable = false)
    private String jenisBarang;

    private String merek;

    @Column(name = "model_seri")
    private String modelSeri;

    /** Menyimpan SN lama ketika terjadi penggantian unit (klaim garansi) */
    @Column(name = "model_seri_lama")
    private String modelSeriLama;

    @Column(name = "nama_barang")
    private String namaBarangStored;

    @Column(name = "nama_barang_norm")
    private String namaBarangNorm;

    @Column(name = "supplier_pembelian")
    private String supplierPembelian;

    @Column(name = "supplier_pembelian_norm")
    private String supplierPembelianNorm;

    @Column(name = "nama_distributor")
    private String namaDistributor;

    @Column(name = "nama_distributor_norm")
    private String namaDistributorNorm;

    @Column(name = "no_nota_supplier")
    private String noNotaSupplier;

    @Column(name = "posisi_barang")
    private String posisiBarang;

    @Column(name = "posisi_sumber")
    private String posisiSumber;

    @Column(name = "posisi_changed_at")
    private LocalDateTime posisiChangedAt;

    @Column(name = "posisi_changed_by")
    private Long posisiChangedBy;

    @Column(name = "sn_verified")
    private Boolean snVerified;

    @Column(name = "sn_verified_at")
    private LocalDateTime snVerifiedAt;

    @Column(columnDefinition = "text")
    private String kelengkapan;

    @Column(columnDefinition = "text")
    private String kerusakan;

    private BigDecimal dp;

    @Column(name = "estimasi_biaya")
    private BigDecimal estimasiBiaya;

    @Column(name = "status_terkini")
    private StatusServis statusTerkini;

    @Column(name = "kondisi_servis")
    private String kondisiServis;

    @Column(name = "ket_tindakan", columnDefinition = "text")
    private String ketTindakan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teknisi_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User teknisi;

    @Column(name = "tgl_ditangani")
    private LocalDateTime tglDitangani;

    @Column(name = "tgl_ambil")
    private LocalDateTime tglAmbil;

    @Column(name = "pengambil_nama")
    private String pengambilNama;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "penyerah_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User penyerah;

    @Column(name = "durasi_garansi")
    private String durasiGaransi;

    @Column(name = "tgl_batas_garansi")
    private LocalDateTime tglBatasGaransi;

    @Column(name = "biaya_final")
    private BigDecimal biayaFinal;

    @Column(name = "modal_sparepart")
    private BigDecimal modalSparepart;

    @Column(name = "status_bayar")
    private StatusPembayaran statusBayar;

    @Column(name = "tgl_jatuh_tempo")
    private LocalDate tglJatuhTempo;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.trackingToken == null) {
            this.trackingToken = UUID.randomUUID();
        }
        if (this.tglTerima == null) {
            this.tglTerima = LocalDateTime.now();
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getNamaBarang() {
        if (namaBarangStored != null && !namaBarangStored.trim().isEmpty()) {
            return namaBarangStored;
        }
        StringBuilder sb = new StringBuilder();
        if (jenisBarang != null) sb.append(jenisBarang);
        if (merek != null && !merek.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(merek);
        }
        return sb.length() > 0 ? sb.toString() : "-";
    }
}
