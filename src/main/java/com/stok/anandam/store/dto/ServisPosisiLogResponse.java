package com.stok.anandam.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServisPosisiLogResponse {
    private Long id;
    private String posisiLama;
    private String posisiBaru;
    private String sumber;
    private String alasan;
    private String changedBy;
    private LocalDateTime createdAt;
}
