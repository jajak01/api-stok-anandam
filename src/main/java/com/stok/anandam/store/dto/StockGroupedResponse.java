package com.stok.anandam.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockGroupedResponse {
    private Long id;
    private String itemCode;
    private String itemName;
    private String kategoriNama;
    private String kategoriItemcode;
    private Integer totalStok;
    private BigDecimal hargaHpp;
    private BigDecimal hargaButtom;
    private BigDecimal grandTotal;
    private String spesifikasi;
    private BigDecimal modal;
    private BigDecimal finalPricelist;
    private LocalDate lastSalesDate;
    private LocalDate lastPurchaseDate;
    private BigDecimal lastPurchasePrice;
    private Boolean isPpn;
    private String parName;
    private List<WarehouseStockDTO> warehouses;
    private Integer totalPending;
    private List<PendingStockDTO> pendingDetails;
}
