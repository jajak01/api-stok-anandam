package com.stok.anandam.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data; // <--- INI OBATNYA
import lombok.NoArgsConstructor;

@Data // <--- Pastikan ini ada
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TokenResponse {
    private String accessToken;
    private String refreshToken; 
    
    @Builder.Default
    private String type = "Bearer";
}