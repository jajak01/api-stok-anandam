package com.stok.anandam.store.core.postgres.model;

/**
 * Standard permission keys for granular authorization across Movva features.
 */
public class Permission {
    // Dashboard & Laporan
    public static final String DASHBOARD = "DASHBOARD";
    public static final String LAPORAN_OMSET = "LAPORAN_OMSET";

    // Stok & Logistik
    public static final String STOK = "STOK";
    public static final String STOK_BADAN = "STOK_BADAN";
    public static final String TKDN = "TKDN";
    public static final String CANVAS = "CANVAS";
    public static final String RAKITAN = "RAKITAN";
    public static final String SIMULASI_SPJ = "SIMULASI_SPJ";

    // Alur Memo & Distribusi
    public static final String MEMO = "MEMO";
    public static final String REQUEST_DELIVERY = "REQUEST_DELIVERY";
    public static final String PENGIRIMAN = "PENGIRIMAN";

    // Transaksi & Gudang
    public static final String PEMBELIAN = "PEMBELIAN";
    public static final String PENJUALAN = "PENJUALAN";
    public static final String PIUTANG = "PIUTANG";
    public static final String PAYMENT_STATUS = "PAYMENT_STATUS";
    public static final String ITEM_SN = "ITEM_SN";
    public static final String IJIN_IMPORT = "IJIN_IMPORT";
    public static final String SHBJ = "SHBJ";
    public static final String DATA_WAREHOUSE = "DATA_WAREHOUSE";
    public static final String DATA_CANVAS = "DATA_CANVAS";

    // Servis & Perbaikan
    public static final String SERVIS = "SERVIS";

    // Lokasi & Tracking
    public static final String LOCATION_TRACK = "LOCATION_TRACK";
    public static final String LOCATION_VIEW = "LOCATION_VIEW";

    // Sistem & Manajemen
    public static final String USERS = "USERS";
    public static final String USER_ACTIVITY = "USER_ACTIVITY";
    public static final String ACTIVITY_LOG = "ACTIVITY_LOG";
    public static final String ANNOUNCEMENT = "ANNOUNCEMENT";
    public static final String PENJADWALAN = "PENJADWALAN";
    public static final String PRINTER = "PRINTER";
    public static final String REMINDER = "REMINDER";
}
