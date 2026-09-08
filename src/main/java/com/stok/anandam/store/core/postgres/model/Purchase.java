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
@Table(name = "purchases", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"doc_no_p", "item_code"})
})
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "purchase_seq_gen")
    @SequenceGenerator(name = "purchase_seq_gen", sequenceName = "purchase_seq", allocationSize = 50)
    private Long id;

    @Column(name = "doc_date")
    private LocalDate docDate;

    @Column(name = "doc_no_p", length = 50)
    private String docNoP;

    @Column(name = "par_name", length = 100)
    private String parName;

    @Column(name = "dep_code", length = 50)
    private String depCode;

    @Column(name = "dep_name")
    private String depName;

    @Column(name = "item_code", length = 50)
    private String itemCode;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "qty")
    private Integer qty;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "grand_total")
    private BigDecimal grandTotal;

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