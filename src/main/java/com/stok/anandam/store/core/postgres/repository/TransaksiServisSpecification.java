     package com.stok.anandam.store.core.postgres.repository;

import com.stok.anandam.store.core.postgres.model.StatusServis;
import com.stok.anandam.store.core.postgres.model.TransaksiServis;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Specification builder untuk filter fleksibel pada TransaksiServis.
 * Mendukung kombinasi filter status, search (noServis & namaPelanggan),
 * dan filter masa garansi.
 */
public class TransaksiServisSpecification {

    /**
     * Mencari berdasarkan noServis, namaPelanggan, noTelepon, atau jenisBarang.
     * Query multi-kata di-split menjadi kata-kata individual, lalu setiap kata
     * dicari di SEMUA kolom (AND antar kata, OR antar kolom).
     *
     * Contoh: "Laptop ACER" → mencari baris yang mengandung "Laptop" AND "ACER"
     * di salah satu kolom: noServis, jenisBarang, namaPelanggan, atau noTelepon.
     */
    public static Specification<TransaksiServis> searchByNoServisOrNamaPelanggan(String search) {
        return (root, query, criteriaBuilder) -> {
            if (search == null || search.trim().isEmpty()) {
                return criteriaBuilder.conjunction(); // no filter
            }

            // Split query menjadi kata-kata individual (dipisah spasi)
            String[] keywords = search.trim().toLowerCase().split("\\s+");

            // Join ke tabel pelanggan (sekali saja)
            var pelangganJoin = root.join("pelanggan", JoinType.LEFT);

            // Untuk setiap kata, buat OR predicate di semua kolom
            List<Predicate> keywordPredicates = new ArrayList<>();

            for (String keyword : keywords) {
                if (keyword.isEmpty()) continue;
                String pattern = "%" + keyword + "%";

                List<Predicate> fieldPredicates = new ArrayList<>();

                // LIKE pada noServis
                fieldPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("noServis")), pattern));

                // LIKE pada jenisBarang (nama barang yang diservis)
                fieldPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("jenisBarang")), pattern));

                // LIKE pada merek (merek barang)
                fieldPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("merek")), pattern));

                fieldPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("namaBarangStored")), pattern));

                fieldPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("namaDistributor")), pattern));

                // LIKE pada namaPelanggan
                fieldPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(pelangganJoin.get("namaPelanggan")), pattern));

                // LIKE pada noTelepon
                fieldPredicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(pelangganJoin.get("noTelepon")), pattern));

                // Normalisasi prefix noTelepon: "62xxx" ↔ "0xxx"
                // Agar user bisa mencari dengan format internasional (62) maupun lokal (08/0)
                String normalizedKeyword = normalizePhonePrefix(keyword);
                if (!normalizedKeyword.equals(keyword)) {
                    String normalizedPattern = "%" + normalizedKeyword + "%";
                    fieldPredicates.add(criteriaBuilder.like(
                            criteriaBuilder.lower(pelangganJoin.get("noTelepon")), normalizedPattern));
                }

                // Kata ini harus muncul di minimal SATU kolom (OR antar kolom)
                keywordPredicates.add(
                        criteriaBuilder.or(fieldPredicates.toArray(new Predicate[0])));
            }

            // Semua kata harus match (AND antar kata)
            return criteriaBuilder.and(keywordPredicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Normalisasi prefix nomor telepon Indonesia.
     * "6281234..." ↔ "081234..." ↔ "81234..."
     */
    private static String normalizePhonePrefix(String phone) {
        if (phone == null || phone.isEmpty()) return phone;
        if (phone.startsWith("62")) {
            return "0" + phone.substring(2);
        }
        if (phone.startsWith("0")) {
            return "62" + phone.substring(1);
        }
        // Coba juga tanpa leading 0/62 (user ketik "8123456" → tambahkan prefix 08 dan 628)
        if (phone.length() >= 8 && Character.isDigit(phone.charAt(0)) && phone.charAt(0) == '8') {
            return "0" + phone;
        }
        return phone;
    }

    /**
     * Filter berdasarkan status terkini.
     */
    public static Specification<TransaksiServis> hasStatus(StatusServis status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("statusTerkini"), status);
        };
    }

    public static Specification<TransaksiServis> hasPosisi(String posisi) {
        return (root, query, criteriaBuilder) -> {
            if (posisi == null || posisi.isBlank()) {
                return root.get("posisiBarang").in("GUDANG_SERVIS", "SUPPLIER", "GUDANG_PUSAT");
            }
            return criteriaBuilder.equal(root.get("posisiBarang"), posisi.trim().toUpperCase());
        };
    }

    public static Specification<TransaksiServis> hasDistributorNorm(String distributorNorm) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("namaDistributorNorm"), distributorNorm);
    }

    /**
     * Filter garansi aktif:
     * statusTerkini = SUDAH_DIAMBIL AND tglBatasGaransi IS NOT NULL AND tglBatasGaransi > sekarang
     */
    public static Specification<TransaksiServis> isGaransiAktif(LocalDateTime sekarang) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("statusTerkini"), StatusServis.SUDAH_DIAMBIL));
            predicates.add(criteriaBuilder.isNotNull(root.get("tglBatasGaransi")));
            predicates.add(criteriaBuilder.greaterThan(root.get("tglBatasGaransi"), sekarang));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter garansi expired:
     * statusTerkini = SUDAH_DIAMBIL AND tglBatasGaransi IS NOT NULL AND tglBatasGaransi <= sekarang
     */
    public static Specification<TransaksiServis> isGaransiExpired(LocalDateTime sekarang) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("statusTerkini"), StatusServis.SUDAH_DIAMBIL));
            predicates.add(criteriaBuilder.isNotNull(root.get("tglBatasGaransi")));
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("tglBatasGaransi"), sekarang));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
