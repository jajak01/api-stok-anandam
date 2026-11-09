package com.stok.anandam.store.service;

import java.util.List;

/** Hasil halaman sederhana: isi + total baris (tanpa Page Spring Data). */
public record PagedResult<T>(List<T> content, long totalElements) {

    public int totalPages(int size) {
        return size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    }
}
