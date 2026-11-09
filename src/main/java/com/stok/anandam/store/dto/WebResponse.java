package com.stok.anandam.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Wrapper standar seluruh response API (sukses maupun error)")
public class WebResponse<T> {
    @Schema(description = "HTTP status code", example = "200")
    private Integer status;

    @Schema(description = "Pesan singkat", example = "Login Berhasil")
    private String message;

    @Schema(description = "Payload response. Untuk error berisi ApiErrorResponse")
    private T data;

    @Schema(description = "Metadata paging (null jika tidak relevan)")
    private PagingResponse paging;
}
