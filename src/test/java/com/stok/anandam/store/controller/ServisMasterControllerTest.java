package com.stok.anandam.store.controller;

import com.stok.anandam.store.dto.SuggestItemResponse;
import com.stok.anandam.store.service.ServisMasterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ServisMasterControllerTest {

    private ServisMasterService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ServisMasterService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ServisMasterController(service)).build();
    }

    @Test
    void suggestBarang_returnsWebResponse() throws Exception {
        when(service.suggestBarang(anyString(), anyInt())).thenReturn(List.of(
                SuggestItemResponse.builder()
                        .nama("EPSON L3210")
                        .normalized("EPSON L3210")
                        .sumber("MASTER")
                        .jumlah(29191L)
                        .build()));

        mockMvc.perform(get("/api/v1/servis/barang/suggest").param("q", "EPS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].nama").value("EPSON L3210"))
                .andExpect(jsonPath("$.data[0].sumber").value("MASTER"));
    }

    @Test
    void suggestSupplier_passesNamaBarang() throws Exception {
        when(service.suggestSupplier(anyString(), anyString(), anyInt())).thenReturn(List.of(
                SuggestItemResponse.builder()
                        .nama("PT RAKA PERKASA TEKNOLOGI")
                        .sumber("RIWAYAT")
                        .build()));

        mockMvc.perform(get("/api/v1/servis/supplier/suggest")
                        .param("q", "RAKA")
                        .param("namaBarang", "EPSON L3210"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nama").value("PT RAKA PERKASA TEKNOLOGI"))
                .andExpect(jsonPath("$.data[0].sumber").value("RIWAYAT"));
    }

    @Test
    void suggestDistributor_returnsClaimSuggestion() throws Exception {
        when(service.suggestDistributor(anyString(), anyInt())).thenReturn(List.of(
                SuggestItemResponse.builder()
                        .nama("ANDALAS")
                        .sumber("KLAIM")
                        .build()));

        mockMvc.perform(get("/api/v1/servis/distributor/suggest").param("q", "AND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nama").value("ANDALAS"))
                .andExpect(jsonPath("$.data[0].sumber").value("KLAIM"));
    }
}
