package com.stok.anandam.store.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KlaimDistributorRekapResponse {
    private String namaDistributor;
    private String namaDistributorNorm;
    private long diDistributor;
    private long totalKlaim;
}
