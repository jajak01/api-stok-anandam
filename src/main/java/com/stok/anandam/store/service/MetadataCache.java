package com.stok.anandam.store.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cache in-memory sederhana dengan TTL untuk metadata yang jarang berubah:
 * daftar karyawan, kategori/dept, dan meta tanggal Data Warehouse.
 *
 * <p>P1 (9 Nov 2026): endpoint metadata ini dipanggil setiap kali halaman
 * dibuka dan sebelumnya selalu menjalankan SELECT DISTINCT ke tabel besar.
 * Dengan TTL 5–10 menit, query itu hanya sesekali dijalankan. Data yang
 * berubah (mis. migrasi menambah karyawan baru) akan terlihat paling lambat
 * setelah TTL habis.</p>
 */
@Component
public class MetadataCache {

    private static final class Entry {
        private final Object value;
        private final long expiresAt;

        private Entry(Object value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }

    private final long ttlMillis;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public MetadataCache(@Value("${app.metadata-cache-ttl-seconds:600}") long ttlSeconds) {
        this.ttlMillis = Math.max(1L, ttlSeconds) * 1000L;
    }

    /** Ambil dari cache; jika kedaluwarsa/belum ada, panggil loader dan simpan. */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        Entry entry = entries.get(key);
        if (entry != null && entry.expiresAt > now) {
            return (T) entry.value;
        }

        synchronized (entries) {
            entry = entries.get(key);
            now = System.currentTimeMillis();
            if (entry != null && entry.expiresAt > now) {
                return (T) entry.value;
            }
            T value = loader.get();
            entries.put(key, new Entry(value, now + ttlMillis));
            return value;
        }
    }

    /** Hapus seluruh cache (dipakai setelah migrasi manual bila perlu). */
    public void invalidateAll() {
        entries.clear();
    }
}
