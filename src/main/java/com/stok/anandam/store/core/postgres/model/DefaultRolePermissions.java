package com.stok.anandam.store.core.postgres.model;

import java.util.*;

/**
 * Provides default initial permission templates based on role.
 * Role is used for initial configuration template, which can subsequently
 * be customized via checklist per account.
 */
public class DefaultRolePermissions {

    private static final Set<String> ALL_PERMISSIONS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            Permission.DASHBOARD,
            Permission.LAPORAN_OMSET,
            Permission.STOK,
            Permission.STOK_BADAN,
            Permission.TKDN,
            Permission.CANVAS,
            Permission.RAKITAN,
            Permission.SIMULASI_SPJ,
            Permission.MEMO,
            Permission.REQUEST_DELIVERY,
            Permission.PENGIRIMAN,
            Permission.PEMBELIAN,
            Permission.PENJUALAN,
            Permission.PIUTANG,
            Permission.PAYMENT_STATUS,
            Permission.LOCATION_TRACK,
            Permission.LOCATION_VIEW,
            Permission.ITEM_SN,
            Permission.IJIN_IMPORT,
            Permission.SHBJ,
            Permission.DATA_WAREHOUSE,
            Permission.DATA_CANVAS,
            Permission.SERVIS,
            Permission.SERVIS_POSISI,
            Permission.USERS,
            Permission.USER_ACTIVITY,
            Permission.ACTIVITY_LOG,
            Permission.ANNOUNCEMENT,
            Permission.PENJADWALAN,
            Permission.PRINTER,
            Permission.REMINDER
    )));

    public static Set<String> getAllPermissions() {
        return ALL_PERMISSIONS;
    }

    public static Set<String> getDefaultPermissions(Role role) {
        if (role == null) {
            return new HashSet<>();
        }

        Set<String> perms = new LinkedHashSet<>();

        switch (role) {
            case ADMIN:
            case MANAGER:
                perms.addAll(ALL_PERMISSIONS);
                break;

            case SPV_MARKETING:
                perms.add(Permission.DASHBOARD);
                perms.add(Permission.STOK);
                perms.add(Permission.STOK_BADAN);
                perms.add(Permission.TKDN);
                perms.add(Permission.CANVAS);
                perms.add(Permission.RAKITAN);
                perms.add(Permission.SIMULASI_SPJ);
                perms.add(Permission.MEMO);
                perms.add(Permission.REQUEST_DELIVERY);
                perms.add(Permission.PENGIRIMAN);
                perms.add(Permission.PEMBELIAN);
                perms.add(Permission.PENJUALAN);
                perms.add(Permission.PIUTANG);
                perms.add(Permission.PAYMENT_STATUS);
                perms.add(Permission.LOCATION_VIEW);
                perms.add(Permission.ITEM_SN);
                perms.add(Permission.DATA_CANVAS);
                perms.add(Permission.PENJADWALAN);
                perms.add(Permission.PRINTER);
                perms.add(Permission.REMINDER);
                break;

            case MARKETING:
            case MARKETING_TOKO:
            case MARKETING_PROJECT:
            case MARKETING_DISTRIBUSI:
            case MARKETING_ONLINE:
                perms.add(Permission.STOK);
                perms.add(Permission.STOK_BADAN);
                perms.add(Permission.TKDN);
                perms.add(Permission.CANVAS);
                perms.add(Permission.RAKITAN);
                perms.add(Permission.SIMULASI_SPJ);
                perms.add(Permission.MEMO);
                perms.add(Permission.REQUEST_DELIVERY);
                perms.add(Permission.PENGIRIMAN);
                perms.add(Permission.PIUTANG);
                perms.add(Permission.PENJADWALAN);
                perms.add(Permission.PRINTER);
                break;

            case SPV_GUDANG:
            case GUDANG:
                perms.add(Permission.DASHBOARD);
                perms.add(Permission.STOK);
                perms.add(Permission.STOK_BADAN);
                perms.add(Permission.RAKITAN);
                perms.add(Permission.ITEM_SN);
                perms.add(Permission.MEMO);
                perms.add(Permission.PENGIRIMAN);
                perms.add(Permission.PENJADWALAN);
                perms.add(Permission.PRINTER);
                break;

            case SPV_TEKNISI:
            case TEKNISI:
                perms.add(Permission.DASHBOARD);
                perms.add(Permission.SERVIS);
                perms.add(Permission.SERVIS_POSISI);
                perms.add(Permission.ITEM_SN);
                perms.add(Permission.MEMO);
                perms.add(Permission.PENGIRIMAN);
                perms.add(Permission.PEMBELIAN);
                perms.add(Permission.PENJUALAN);
                perms.add(Permission.DATA_WAREHOUSE);
                perms.add(Permission.PENJADWALAN);
                perms.add(Permission.PRINTER);
                break;

            case DELIVERY:
                perms.add(Permission.PENGIRIMAN);
                perms.add(Permission.PENJADWALAN);
                perms.add(Permission.LOCATION_TRACK);
                break;

            case NOTA:
                perms.add(Permission.MEMO);
                perms.add(Permission.PENGIRIMAN);
                break;
        }

        return perms;
    }
}
