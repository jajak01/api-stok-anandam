package com.stok.anandam.store.core.postgres.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "sales", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"doc_no", "item_name"})
})
public class Sales {

    @Id
    // Optimasi Batch Insert: Gunakan SEQUENCE agar Hibernate bisa menumpuk insert
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_seq_gen")
    @SequenceGenerator(name = "sales_seq_gen", sequenceName = "sales_seq", allocationSize = 50)
    private Long id;

    @Column(name = "doc_date")
    private LocalDate docDate;

    @Column(name = "doc_no")
    private String docNo;

    @Column(name = "code", length = 50) // Partner Code
    private String code;

    @Column(name = "par_name")
    private String parName;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "dep_code", length = 50)
    private String depCode;

    @Column(name = "dep_name")
    private String depName;

    @Column(name = "ite_code", length = 50)
    private String iteCode;

    @Column(name = "qty")
    private Integer qty;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "grand_total")
    private BigDecimal grandTotal;

    @Column(name = "hpp_satuan", precision = 19, scale = 6)
    private BigDecimal hppSatuan;

    @Column(name = "total_hpp", precision = 19, scale = 6)
    private BigDecimal totalHpp;

    @Column(name = "laba_kotor", precision = 19, scale = 6)
    private BigDecimal labaKotor;

    @Column(name = "emp_code", length = 50)
    private String empCode;

    @Column(name = "emp_name")
    private String empName;

    @Column(name = "paid_total", precision = 19, scale = 2)
    private BigDecimal paidTotal;

    @Column(name = "status_bayar", length = 20)
    private String statusBayar;

    @Column(name = "last_synced")
    private LocalDateTime lastSynced;
}