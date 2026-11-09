package com.stok.anandam.store.service;

import jakarta.persistence.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builder predikat JPQL dinamis.
 *
 * <p>P1 (9 Nov 2026): menggantikan pola {@code (:param IS NULL OR ...)} yang
 * membuat PostgreSQL menyiapkan satu rencana query "generik" (dan sering
 * memilih seq scan) walaupun parameternya kosong. Dengan builder ini hanya
 * kondisi yang benar-benar dipakai yang masuk ke JPQL, sehingga planner bisa
 * memakai index yang tepat.</p>
 *
 * <p>Semua nilai tetap dikirim sebagai parameter terikat (tidak ada
 * interpolasi string dari input user). Nama kolom hanya berasal dari whitelist
 * di DAO pemanggil.</p>
 */
public final class JpqlSearchFilter {

    private final String alias;
    private final StringBuilder where = new StringBuilder();
    private final Map<String, Object> params = new LinkedHashMap<>();

    public JpqlSearchFilter(String alias) {
        this.alias = alias;
    }

    public JpqlSearchFilter dateRange(LocalDate start, LocalDate end) {
        if (start != null) {
            and(alias + ".docDate >= :startDate");
            params.put("startDate", start);
        }
        if (end != null) {
            and(alias + ".docDate <= :endDate");
            params.put("endDate", end);
        }
        return this;
    }

    /** Tambahkan {@code alias.field IN (:param)} hanya jika nilainya ada. */
    public JpqlSearchFilter in(String field, String paramName, Collection<String> values) {
        if (values != null && !values.isEmpty()) {
            and(alias + "." + field + " IN (:" + paramName + ")");
            params.put(paramName, new ArrayList<>(values));
        }
        return this;
    }

    /**
     * Filter pencarian teks. {@code searchColumn} menentukan kolom yang
     * dicari; nilai kosong / {@code ALL} berarti semua kolom.
     */
    public JpqlSearchFilter search(String docNoField, String parNameField, String itemNameField,
            String depCodeField, String codeField, String search, String searchColumn) {
        if (search == null || search.trim().isEmpty()) {
            return this;
        }
        String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        params.put("searchTerm", term);

        List<String> fields = new ArrayList<>();
        String column = searchColumn == null ? "" : searchColumn.trim();
        if (column.isEmpty() || column.equalsIgnoreCase("ALL")) {
            fields.add(docNoField);
            fields.add(parNameField);
            fields.add(itemNameField);
            fields.add(depCodeField);
            if (codeField != null) {
                fields.add(codeField);
            }
        } else if (column.equalsIgnoreCase("code") && codeField != null) {
            fields.add(codeField);
        } else if (column.equalsIgnoreCase("noNota")) {
            fields.add(docNoField);
        } else if (column.equalsIgnoreCase("distributor")) {
            fields.add(parNameField);
        } else if (column.equalsIgnoreCase("barang")) {
            fields.add(itemNameField);
        } else if (column.equalsIgnoreCase("dept")) {
            fields.add(depCodeField);
        } else if (column.equalsIgnoreCase("iteCode") && codeField != null) {
            fields.add(codeField);
        } else {
            // Kolom tak dikenal: perlakukan seperti ALL agar tidak mengembalikan
            // hasil kosong secara diam-diam.
            fields.add(docNoField);
            fields.add(parNameField);
            fields.add(itemNameField);
            fields.add(depCodeField);
            if (codeField != null) {
                fields.add(codeField);
            }
        }

        StringBuilder or = new StringBuilder();
        for (String field : fields) {
            if (or.length() > 0) {
                or.append(" OR ");
            }
            or.append("LOWER(").append(alias).append('.').append(field).append(") LIKE :searchTerm");
        }
        and("(" + or + ")");
        return this;
    }

    /**
     * Filter status pembayaran (tab PAID/UNPAID) untuk Piutang/Hutang.
     * Nilai teks tidak berasal dari user sehingga aman ditulis inline.
     */
    public JpqlSearchFilter paymentStatus(String field, boolean isPaid) {
        String path = alias + "." + field;
        if (isPaid) {
            and("UPPER(TRIM(" + path + ")) IN ('PAID','LUNAS','Lunas')");
        } else {
            and("(" + path + " IS NULL OR UPPER(TRIM(" + path + ")) IN "
                    + "('UNPAID','BELUM LUNAS','Belum Lunas','JATUH TEMPO','Jatuh Tempo'))");
        }
        return this;
    }

    /** Klausa WHERE siap tempel (kosong jika tidak ada filter). */
    public String whereClause() {
        return where.length() == 0 ? "" : " WHERE " + where;
    }

    /** Urutan ORDER BY dari {@link Pageable}, dibatasi whitelist kolom. */
    public String orderBy(Pageable pageable, String defaultField, Set<String> allowedFields) {
        String field = defaultField;
        Sort.Direction direction = Sort.Direction.DESC;
        if (pageable != null && pageable.getSort() != null && pageable.getSort().isSorted()) {
            Sort.Order order = pageable.getSort().iterator().next();
            if (allowedFields.contains(order.getProperty())) {
                field = order.getProperty();
                direction = order.getDirection();
            }
        }
        return " ORDER BY " + alias + "." + field + " " + direction.name();
    }

    /** Bind semua parameter ke query. */
    public void bind(Query query) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
    }

    private JpqlSearchFilter and(String condition) {
        if (where.length() > 0) {
            where.append(" AND ");
        }
        where.append(condition);
        return this;
    }
}
