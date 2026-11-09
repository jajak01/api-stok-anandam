package com.stok.anandam.store.config;

import com.stok.anandam.store.service.ServisMasterService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seed master_barang saat startup hanya jika tabel kosong.
 * Refresh harian ditangani {@link ServisMasterService#refreshDaily()}.
 */
@Component
public class ServisMasterRefreshRunner implements ApplicationRunner {

    private final ServisMasterService servisMasterService;

    public ServisMasterRefreshRunner(ServisMasterService servisMasterService) {
        this.servisMasterService = servisMasterService;
    }

    @Override
    public void run(ApplicationArguments args) {
        servisMasterService.seedIfEmpty();
    }
}
