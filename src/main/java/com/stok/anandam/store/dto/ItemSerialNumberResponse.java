package com.stok.anandam.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemSerialNumberResponse {
    private LocalDateTime tanggal;
    private String docId;
    private String user;
    private String itemName;
    private String sn;
    /** Supplier pembelian (purchases.par_name) untuk dokumen MASUK. */
    private String supplier;
    /** Tipe dokumen SN: MASUK / KELUAR. */
    private String docType;
}