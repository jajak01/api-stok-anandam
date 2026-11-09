package com.stok.anandam.store.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Konfigurasi koneksi ke database Portal (eksternal) untuk membaca tabel
 * Reminder (Menu Reminder Canvasing). Terpisah dari database utama (pg).
 */
@Configuration
public class PortalConfig {

    @Value("${spring.datasource.portal.jdbc-url}")
    private String url;

    @Value("${spring.datasource.portal.username}")
    private String username;

    @Value("${spring.datasource.portal.password}")
    private String password;

    @Value("${spring.datasource.portal.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.portal.hikari.maximum-pool-size:5}")
    private int maximumPoolSize;

    @Value("${spring.datasource.portal.hikari.minimum-idle:2}")
    private int minimumIdle;

    @Value("${spring.datasource.portal.hikari.connection-timeout:5000}")
    private long connectionTimeout;

    @Bean(name = "portalDataSource")
    public DataSource portalDataSource() {
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        ds.setMaximumPoolSize(maximumPoolSize);
        ds.setMinimumIdle(minimumIdle);
        ds.setConnectionTimeout(connectionTimeout);
        ds.setPoolName("HikariPool-Portal");
        return ds;
    }

    @Bean(name = "portalJdbcTemplate")
    public org.springframework.jdbc.core.JdbcTemplate portalJdbcTemplate(
            @Qualifier("portalDataSource") DataSource dataSource) {
        return new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }
}