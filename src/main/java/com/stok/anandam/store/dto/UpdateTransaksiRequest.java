package com.stok.anandam.store.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class UpdateTransaksiRequest {
    private UUID pelangganId;
    private String jenisBarang;
    private String merek;
    private String namaBarang;
    private String supplierPembelian;
    private String namaDistributor;
    private String noNotaSupplier;
    private String modelSeri;
    private String kelengkapan;
    private String kerusakan;
    private BigDecimal dp;
    private BigDecimal estimasiBiaya;
}
