package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.TransaksiServis;
import com.stok.anandam.store.core.postgres.repository.TransaksiServisRepository;
import com.stok.anandam.store.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaLinkService {

    private final TransaksiServisRepository transaksiRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.tracking-url}")
    private String trackingUrl;

    // Wajib transaksional: spring.jpa.open-in-view=false, sedangkan transaksi.getPelanggan()
    // adalah relasi LAZY. Tanpa transaksi aktif akan muncul LazyInitializationException (HTTP 500).
    @Transactional
    public String generateWaLink(UUID transaksiId, String tipePesan) {
        // 1. Cari data transaksi
        TransaksiServis transaksi = transaksiRepository.findById(transaksiId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaksi servis dengan ID " + transaksiId + " tidak ditemukan."));

        // 2. Ambil data yang dibutuhkan
        String nomorHp = formatNomorHp(transaksi.getPelanggan().getNoTelepon());
        String nama = transaksi.getPelanggan().getNamaPelanggan();
        String idNota = transaksi.getId().toString();

        // Data lama bisa saja belum punya tracking_token, buatkan agar link tracking tidak error
        if (transaksi.getTrackingToken() == null) {
            transaksi.setTrackingToken(UUID.randomUUID());
            transaksiRepository.save(transaksi);
        }
        String trackingToken = transaksi.getTrackingToken().toString();
        
        // Ambil data detail barang & keluhan
        String barangService = transaksi.getNamaBarang();
        String keluhan = transaksi.getKerusakan() != null ? transaksi.getKerusakan() : "-";
        
        // Link untuk nota (PDF) tetap pakai baseUrl API dan link untuk tracking web pakai domain tracking
        String linkNota = baseUrl + "/api/v1/nota/download/" + idNota;
        String linkTracking = trackingUrl + "/track/servis/" + trackingToken;
        
        // 3. Susun template pesan berdasarkan tipe
        String pesan = "";
        
        if ("DITERIMA".equalsIgnoreCase(tipePesan)) {
            // Format tanggal: "08 Juni 2026 jam 14:56 WIB"
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy 'jam' HH:mm 'WIB'", new Locale("id", "ID"));
            String tanggalMasuk = transaksi.getCreatedAt() != null ? transaksi.getCreatedAt().format(formatter) : "-";

            pesan = "*Notifikasi | ANANDAM ID (ANANDAM INDONESIA)*\n" +
                    "Atas Nama : *" + nama + "*\n" +
                    "Barang Service : *" + barangService + "*\n" +
                    "Keluhan : *" + keluhan + "*\n\n" +
                    "Telah diterima oleh Anandam.ID pada tanggal " + tanggalMasuk + "\n" +
                    "Dengan No Service :\n" +
                    "*" + idNota + "*\n\n" +
                    "Silahkan buka link dibawah ini untuk Cek Status (Tracking) Service barang anda.\n" +
                    linkTracking;

        } else if ("SELESAI".equalsIgnoreCase(tipePesan)) {
            // Setup format Rupiah
            NumberFormat formatRupiah = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
            java.math.BigDecimal totalBiaya = transaksi.getBiayaFinal() != null ? transaksi.getBiayaFinal() : transaksi.getEstimasiBiaya();
            String biaya = totalBiaya != null 
                           ? formatRupiah.format(totalBiaya).replace("Rp", "Rp ") 
                           : "Rp 0";

            pesan = "Halo Kak *" + nama + "*, Servis barang Anda (*" + barangService + "*) di Anandam Computer sudah *SELESAI*\n" +
                    "Atas Nama : *" + nama + "*\n" +
                    "Barang Service : *" + barangService + "*\n" +
                    "Keluhan : *" + keluhan + "*\n\n" +
                    "Telah Selesai Service dan Bisa Diambil di Anandam.ID dengan biaya *" + biaya + "*.\n\n" +
                    "Silahkan buka link dibawah ini untuk Cek Status (Tracking) Service barang anda.\n" +
                    linkTracking;

        } else if ("KENDALA".equalsIgnoreCase(tipePesan)) {
            pesan = "Halo Kak *" + nama + "*, Ada beberapa hal yang perlu kami diskusikan mengenai service\n" +
                    "Atas Nama : *" + nama + "*\n" +
                    "Barang Service : *" + barangService + "*\n" +
                    "Keluhan : *" + keluhan + "*\n\n" +
                    "Mohon balas WA ini untuk berdiskusi lebih lanjut.\n\n" +
                    "Silahkan buka link dibawah ini untuk Cek Status (Tracking) Service barang anda.\n" +
                    linkTracking;
        }

        // 4. Encode pesan agar aman dimasukkan ke dalam link URL
        String encodedPesan = URLEncoder.encode(pesan, StandardCharsets.UTF_8).replace("+", "%20");
        
        // 5. Kembalikan link WhatsApp yang utuh
        return "https://wa.me/" + nomorHp + "?text=" + encodedPesan;
    }

    private String formatNomorHp(String nomor) {
        if (nomor == null) return nomor;
        
        // Ambil hanya digit angka dari nomor telepon (untuk menghapus prefix seperti "Telp. ",  "WA: ", dll)
        String digitsOnly = nomor.replaceAll("[^0-9]", "");
        
        if (digitsOnly.isEmpty()) return nomor;
        
        if (digitsOnly.startsWith("0")) {
            return "62" + digitsOnly.substring(1);
        }
        return digitsOnly;
    }
}