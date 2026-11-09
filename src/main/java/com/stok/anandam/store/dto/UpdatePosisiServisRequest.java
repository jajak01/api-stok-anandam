package com.stok.anandam.store.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePosisiServisRequest {
    @NotBlank
    private String posisi;

    @NotBlank
    private String alasan;
}
