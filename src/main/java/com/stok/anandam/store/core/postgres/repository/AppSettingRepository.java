package com.stok.anandam.store.core.postgres.repository;

import com.stok.anandam.store.core.postgres.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
