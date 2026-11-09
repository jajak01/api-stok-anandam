package com.stok.anandam.store.core.postgres.repository;

import com.stok.anandam.store.core.postgres.model.KlaimDistributor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KlaimDistributorRepository extends JpaRepository<KlaimDistributor, UUID> {

    // P1: OSIV dimatikan; muat relasi lazy yang dipakai KlaimDistributorResponse.
    @EntityGraph(attributePaths = { "transaksi", "createdBy" })
    java.util.Optional<KlaimDistributor> findByTransaksiId(java.util.UUID transaksiId);

    java.util.List<KlaimDistributor> findByTransaksiIdIn(java.util.List<UUID> transaksiIds);
}
