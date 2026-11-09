package com.stok.anandam.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Satu baris saran autocomplete untuk nama barang, supplier pembelian,
 * atau distributor klaim.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SuggestItemResponse {
    private String nama;
    private String normalized;
    /** MASTER | RIWAYAT | KLAIM */
    private String sumber;
    private Long jumlah;
    private LocalDateTime terakhir;
}
