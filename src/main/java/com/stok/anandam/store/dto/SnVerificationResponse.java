package com.stok.anandam.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Hasil verifikasi satu Serial Number: BL (SN Masuk), JL (SN Keluar), dan
 * data lama (BL lama), semuanya exact match dalam satu response.
 *
 * <p>P1 (9 Nov 2026): menggantikan 3 request berurutan dari form Servis.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnVerificationResponse {
    private String sn;
    /** SN Masuk / BL. */
    private List<ItemSerialNumberResponse> masuk;
    /** SN Keluar / JL. */
    private List<ItemSerialNumberResponse> keluar;
    /** Data Warehouse Item SN (BL lama). */
    private List<ItemSerialNumberResponse> old;
    /** True jika SN ditemukan di salah satu sumber. */
    private boolean verified;
}
