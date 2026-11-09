package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.*;
import com.stok.anandam.store.core.postgres.repository.*;
import com.stok.anandam.store.dto.StockSummaryByCategoryResponse;
import com.stok.anandam.store.dto.StockSummaryRowResponse;
import com.stok.anandam.store.dto.StockGroupedResponse;
import com.stok.anandam.store.dto.WarehouseStockDTO;
import com.stok.anandam.store.dto.StokBadanItem;
import com.stok.anandam.store.dto.StokBadanGroupResponse;
import com.stok.anandam.store.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StockService {

        @Autowired
        private StockRepository stockRepository;

        @Autowired
        private AppSettingsService appSettingsService;

        @Autowired
        private SalesRepository salesRepository;

        @Autowired
        private PricelistRepository pricelistRepository;

        @Autowired
        private PurchaseRepository purchaseRepository;

        @Autowired
        private MemoItemRepository memoItemRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private DistributorRepository distributorRepository;

        @Autowired
        private OldPurchaseRepository oldPurchaseRepository;

        @Autowired
        @org.springframework.beans.factory.annotation.Qualifier("pgJdbcTemplate")
        private org.springframework.jdbc.core.JdbcTemplate pgJdbcTemplate;

        /**
         * HPP (harga_hpp) dan Modal Final (pricelist.modal) adalah data biaya internal.
         * Hanya SPV (semua divisi), ADMIN, dan MANAGER yang boleh menerimanya dari API.
         */
        private static boolean canViewCost(Role role) {
                if (role == null)
                        return false;
                return role == Role.ADMIN
                                || role == Role.MANAGER
                                || role.name().startsWith("SPV");
        }

        public Page<StockGroupedResponse> getGroupedStocks(int page, int size, String sortBy, String direction,
                        String search, List<String> categories, String username) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
                Role role = user.getRole();
                // HPP & Modal Final hanya untuk SPV, ADMIN, dan MANAGER.
                boolean canSeeCost = canViewCost(role);

                // Map nama distributor -> entitas distributor (untuk menentukan PPN / NON PPN)
                Map<String, Distributor> distributorByName = buildDistributorMap();

                Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? Sort.Direction.DESC
                                : Sort.Direction.ASC;
                String actualSortBy = sortBy;
                // Map frontend sort names to backend field names if they differ
                if ("modalFinal".equals(sortBy))
                        actualSortBy = "p.modal";
                else if ("finalPricelist".equals(sortBy))
                        actualSortBy = "p.finalPricelist";
                else if ("spesifikasi".equals(sortBy))
                        actualSortBy = "p.spesifikasi";
                else if ("itemCode".equals(sortBy))
                        actualSortBy = "s.itemCode";
                else if ("itemName".equals(sortBy))
                        actualSortBy = "s.itemName";
                else if ("finalStok".equals(sortBy))
                        actualSortBy = "SUM(s.finalStok)";
                else
                        actualSortBy = "s.itemName";

                Pageable pageable = PageRequest.of(page, size); // Manual sorting in query if complex

                Page<String> itemCodePage;
                if (actualSortBy.startsWith("p.") || actualSortBy.startsWith("SUM") || actualSortBy.equals("s.itemName")) {
                        // Need special query for joined/agg sorting
                        itemCodePage = stockRepository.findDistinctItemCodesSortedByPricelist(
                                        search, categories, actualSortBy, direction, pageable);
                } else {
                        itemCodePage = stockRepository.findDistinctItemCodes(search, categories,
                                        PageRequest.of(page, size,
                                                        Sort.by(sortDirection, actualSortBy.replace("s.", ""))));
                }
                List<String> itemCodes = itemCodePage.getContent();
                if (itemCodes.isEmpty()) {
                        return Page.empty(pageable);
                }

                List<Stock> allStocks = stockRepository.findByItemCodeInAndFinalStokGreaterThanEqual(itemCodes, 1);

                // Fetch Last Sales Dates for these items
                List<String> itemNames = allStocks.stream()
                                .map(Stock::getItemName)
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .distinct()
                                .collect(Collectors.toList());

                Map<String, LocalDate> lastSalesDates = new HashMap<>();
                if (!itemNames.isEmpty()) {
                        List<Object[]> results = salesRepository.findLatestDocDatesByItemNames(itemNames);
                        for (Object[] res : results) {
                                String name = (String) res[0];
                                if (name != null) {
                                        lastSalesDates.put(name.trim().toLowerCase(), (LocalDate) res[1]);
                                }
                        }
                }

                // Fetch Last Purchase Details (Date and Partner Name)
                Map<String, LocalDate> lastPurchaseDates = new HashMap<>();
                Map<String, String> lastPurchasePartners = new HashMap<>();
                Map<String, BigDecimal> lastPurchasePrice = new HashMap<>();
                if (!itemNames.isEmpty()) {
                        // Pre-process: TRIM + lowercase agar cocok dengan kondisi WHERE TRIM(LOWER(item_name)) IN (:itemNames)
                        List<String> normalizedItemNames = itemNames.stream()
                                .map(n -> n.trim().toLowerCase())
                                .distinct()
                                .collect(Collectors.toList());

                        List<Object[]> results = purchaseRepository.findLatestPurchaseDetailsByItemNames(normalizedItemNames);
                        for (Object[] res : results) {
                                String name = (String) res[0];

                                // Native query returns java.sql.Date, not LocalDate — perlu konversi
                                LocalDate date = null;
                                if (res[1] != null) {
                                        if (res[1] instanceof LocalDate) {
                                                date = (LocalDate) res[1];
                                        } else if (res[1] instanceof java.sql.Date) {
                                                date = ((java.sql.Date) res[1]).toLocalDate();
                                        } else {
                                                date = java.sql.Date.valueOf(res[1].toString()).toLocalDate();
                                        }
                                }

                                String partner = (String) res[2];
                                BigDecimal price = null;
                                if (res[3] != null) {
                                    if (res[3] instanceof BigDecimal) {
                                        price = (BigDecimal) res[3];
                                    } else {
                                        price = new BigDecimal(res[3].toString());
                                    }
                                }

                                if (name != null) {
                                        // Bersihkan prefix distributor dan key map pakai lowercase
                                        String cleanedPartner = MigrationService.cleanDistributorName(partner);
                                        lastPurchaseDates.put(name.trim().toLowerCase(), date);
                                        lastPurchasePartners.put(name.trim().toLowerCase(), cleanedPartner);
                                        lastPurchasePrice.put(name.trim().toLowerCase(), price);
                                }
                        }

                        // Fallback: untuk item yang tidak ditemukan di purchases ATAU parName-nya "PERSEDIAAN AWAL", cari di old_purchase
                        List<String> missingItemNames = itemNames.stream()
                                .map(n -> n.trim().toLowerCase())
                                .filter(n -> !lastPurchasePartners.containsKey(n) || "PERSEDIAAN AWAL".equalsIgnoreCase(lastPurchasePartners.get(n)))
                                .distinct()
                                .collect(Collectors.toList());

                        if (!missingItemNames.isEmpty()) {
                                List<String> normalizedOldNames = missingItemNames.stream()
                                        .map(n -> n.trim().toLowerCase())
                                        .distinct()
                                        .collect(Collectors.toList());

                                List<Object[]> oldResults = oldPurchaseRepository
                                                .findLatestPurchaseDetailsByItemNames(normalizedOldNames);
                                for (Object[] res : oldResults) {
                                        String name = (String) res[0];

                                        LocalDate date = null;
                                        if (res[1] != null) {
                                                if (res[1] instanceof LocalDate) {
                                                        date = (LocalDate) res[1];
                                                } else if (res[1] instanceof java.sql.Date) {
                                                        date = ((java.sql.Date) res[1]).toLocalDate();
                                                } else {
                                                        date = java.sql.Date.valueOf(res[1].toString()).toLocalDate();
                                                }
                                        }

                                        String partner = (String) res[2];
                                        BigDecimal price = null;
                                        if (res[3] != null) {
                                                if (res[3] instanceof BigDecimal) {
                                                        price = (BigDecimal) res[3];
                                                } else {
                                                        price = new BigDecimal(res[3].toString());
                                                }
                                        }

                                        if (name != null) {
                                                // Bersihkan prefix distributor dan key pakai lowercase
                                                String cleanedPartner = MigrationService.cleanDistributorName(partner);
                                                lastPurchaseDates.put(name.trim().toLowerCase(), date);
                                                lastPurchasePartners.put(name.trim().toLowerCase(), cleanedPartner);
                                                lastPurchasePrice.put(name.trim().toLowerCase(), price);
                                        }
                                }
                        }
                }

                Map<String, List<Stock>> groupedByCode = allStocks.stream()
                                .collect(Collectors.groupingBy(Stock::getItemCode));

                // Fetch pricelist data for all items in one go (Optimized: Fix N+1)
                List<String> normalizedNames = itemNames.stream()
                                .map(com.stok.anandam.store.util.NormalizationUtil::normalizeItemName)
                                .collect(Collectors.toList());
                
                Map<String, com.stok.anandam.store.core.postgres.model.Pricelist> pricelistMap = new HashMap<>();
                pricelistRepository.findByNormalizedItemNameIn(normalizedNames).forEach(p -> {
                    // Map back to original name for lookup convenience in the stream below
                    // We need to find which original name matches this normalized one
                    itemNames.stream()
                        .filter(original -> com.stok.anandam.store.util.NormalizationUtil.normalizeItemName(original).equals(p.getNormalizedItemName()))
                        .forEach(original -> pricelistMap.put(original, p));
                });

                List<StockGroupedResponse> groupedResponses = itemCodes.stream().map(code -> {
                        List<Stock> stocks = groupedByCode.get(code);
                        if (stocks == null || stocks.isEmpty()) return null;
                        
                        // Pick the best stock record to represent this code (priority to one matching search term)
                        Stock displayStock = stocks.get(0);
                        if (search != null && !search.isEmpty()) {
                            final String searchLower = search.toLowerCase();
                            displayStock = stocks.stream()
                                .filter(s -> s.getItemName() != null && s.getItemName().toLowerCase().contains(searchLower))
                                .findFirst()
                                .orElse(displayStock);
                        }
                        
                        String trimmedName = displayStock.getItemName() != null ? displayStock.getItemName().trim() : "";
                        String lookupKey = trimmedName.toLowerCase();

                        List<WarehouseStockDTO> warehouses = stocks.stream()
                                        .map(s -> WarehouseStockDTO.builder()
                                                         .warehouse(s.getWarehouse())
                                                         .stok(s.getFinalStok())
                                                         .build())
                                        .collect(Collectors.toList());

                        int totalStok = stocks.stream().mapToInt(Stock::getFinalStok).sum();
                        BigDecimal grandTotal = stocks.stream()
                                        .map(Stock::getGrandTotal)
                                        .filter(Objects::nonNull)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                        com.stok.anandam.store.core.postgres.model.Pricelist priceInfo = pricelistMap
                                        .get(trimmedName);

                        // Harga Buttom = Modal Final + (Modal Final * percent / 100).
                        // Fallback ke HPP hanya bila Modal Final tidak tersedia (barang tanpa pricelist).
                        BigDecimal modalFinal = priceInfo != null ? priceInfo.getModal() : null;
                        BigDecimal hargaButtomBase = (modalFinal != null && modalFinal.signum() > 0)
                                        ? modalFinal
                                        : displayStock.getHargaHpp();
                        BigDecimal hargaButtom = null;
                        if (hargaButtomBase != null && hargaButtomBase.signum() > 0) {
                                BigDecimal percent = appSettingsService.getHargaButtomPercent();
                                BigDecimal factor = percent.add(new BigDecimal("100"))
                                                .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
                                hargaButtom = hargaButtomBase.multiply(factor).setScale(2, RoundingMode.HALF_UP);
                        }

                        StockGroupedResponse res = StockGroupedResponse.builder()
                                        .id(displayStock.getId())
                                        .itemCode(displayStock.getItemCode())
                                        .itemName(displayStock.getItemName())
                                        .kategoriNama(displayStock.getKategoriNama())
                                        .kategoriItemcode(displayStock.getKategoriItemcode())
                                        .totalStok(totalStok)
                                        .hargaHpp(displayStock.getHargaHpp())
                                        .hargaButtom(hargaButtom)
                                        .grandTotal(grandTotal)
                                        .spesifikasi(priceInfo != null ? priceInfo.getSpesifikasi() : null)
                                        .modal(priceInfo != null ? priceInfo.getModal() : null)
                                        .finalPricelist(priceInfo != null ? priceInfo.getFinalPricelist() : null)
                                        .lastSalesDate(lastSalesDates.get(lookupKey))
                                        .lastPurchaseDate(lastPurchaseDates.get(lookupKey))
                                         .lastPurchasePrice(lastPurchasePrice.get(lookupKey))
                                         .parName(lastPurchasePartners.get(lookupKey))
                                         .isPpn(resolveIsPpn(lastPurchasePartners.get(lookupKey), distributorByName, displayStock.getIsPpn()))
                                         .warehouses(warehouses)
                                         .totalPending(0) // Default
                                         .pendingDetails(new ArrayList<>()) // Default
                                         .build();

                        if (!canSeeCost) {
                            res.setHargaHpp(null);
                            res.setModal(null);
                            res.setGrandTotal(null);
                            res.setLastPurchasePrice(null);
                        }
                        return res;
                }).filter(Objects::nonNull).collect(Collectors.toList());

                // Fetch all Pending Items (Only Approved ones) with JOIN FETCH optimization (Fix connection pool exhaustion)
                List<MemoItem> allPendingItems = memoItemRepository.findByMemo_StatusAkhirWithMemoAndMarketing(com.stok.anandam.store.core.postgres.model.enums.MemoStatus.DISETUJUI);
                
                // Group all pending items by normalized name
                Map<String, List<MemoItem>> pendingByName = allPendingItems.stream()
                        .collect(Collectors.groupingBy(item -> com.stok.anandam.store.util.NormalizationUtil.normalizeItemName(item.getNamaBarang())));

                // Enrich groupedResponses with pending info by matching normalized itemName
                for (StockGroupedResponse resp : groupedResponses) {
                    String respNormalized = com.stok.anandam.store.util.NormalizationUtil.normalizeItemName(resp.getItemName());
                    List<MemoItem> items = pendingByName.get(respNormalized);
                    if (items != null) {
                        int totalPending = items.stream().mapToInt(MemoItem::getQty).sum();
                        // Group by marketing name and sum quantities
                        Map<String, Integer> groupedDetails = items.stream()
                                .collect(Collectors.groupingBy(item -> {
                                    if (item.getMemo() == null) return "Unknown";
                                    if (item.getMemo().getMarketing() != null) {
                                        return item.getMemo().getMarketing().getNama();
                                    }
                                    if (item.getMemo().getMarketingName() != null && !item.getMemo().getMarketingName().isEmpty()) {
                                        return item.getMemo().getMarketingName();
                                    }
                                    return "Unknown";
                                }, Collectors.summingInt(MemoItem::getQty)));

                        List<com.stok.anandam.store.dto.PendingStockDTO> details = groupedDetails.entrySet().stream()
                                .map(entry -> com.stok.anandam.store.dto.PendingStockDTO.builder()
                                        .marketingNama(entry.getKey())
                                        .qty(entry.getValue())
                                        .build())
                                .collect(Collectors.toList());
                        
                        resp.setTotalPending(totalPending);
                        resp.setPendingDetails(details);
                    }
                }

                return new org.springframework.data.domain.PageImpl<>(groupedResponses, pageable,
                                itemCodePage.getTotalElements());
        }

        public List<StockSummaryByCategoryResponse> getSummaryByCategory(String groupBy) {
                List<Object[]> results;
                if ("kategori_nama".equalsIgnoreCase(groupBy)) {
                        results = stockRepository.sumGrandTotalByKategoriNama();
                } else {
                        results = stockRepository.sumGrandTotalByKategoriItemcode();
                }

                BigDecimal totalAll = stockRepository.sumAllGrandTotal();
                List<StockSummaryByCategoryResponse> data = new ArrayList<>();

                for (Object[] res : results) {
                        String name = (String) res[0];
                        BigDecimal grandTotal = (BigDecimal) res[1];
                        BigDecimal percentage = totalAll.compareTo(BigDecimal.ZERO) > 0
                                        ? grandTotal.multiply(new BigDecimal(100)).divide(totalAll, 2,
                                                        RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;

                        data.add(StockSummaryByCategoryResponse.builder()
                                        .nama(name)
                                        .stok(grandTotal)
                                        .presentase(percentage)
                                        .build());
                }

                return data;
        }

        public List<StockSummaryRowResponse> getSummaryByCategoryHierarchy() {
                List<Object[]> rawResults = stockRepository.sumGrandTotalByKategoriHierarchy();
                BigDecimal totalAll = stockRepository.sumAllGrandTotal();

                // 1. Define the manual order and mapping based on the image
                // key: Parent Name (from image), value: List of sub-category prefixes/codes
                // from DB
                Map<String, List<String>> manualHierarchy = new LinkedHashMap<>();
                manualHierarchy.put("2ND", List.of("2ND"));
                manualHierarchy.put("PROJEKTOR", List.of("PROJEKTOR", "PROJ", "PROJECTOR", "PJT"));
                manualHierarchy.put("UPS", List.of("UPS"));
                manualHierarchy.put("BRANDED", List.of("PCAIO", "PCBU", "PCMINI"));
                manualHierarchy.put("NETWORK", List.of("NETWORK", "NET", "NWK"));
                manualHierarchy.put("NOTEBOOK", List.of("NOTEBOOK", "NB"));
                manualHierarchy.put("MONITOR", List.of("MONITOR", "MON", "LCD"));
                manualHierarchy.put("KOMPONEN", List.of("PROC", "MB", "VGA", "RAM", "SSD", "SSDEX", "HDIN3", "HDIN2",
                                "HDEX3", "HDEX2", "CS", "PSU", "CLR", "FAN"));
                manualHierarchy.put("CTRD TINTA TONER", List.of("TINTA", "CARTD"));
                manualHierarchy.put("PRINTER SCANNER", List.of("PRINT", "SCAN"));
                manualHierarchy.put("ACC", List.of("ACS", "AL", "ATK", "BRKT", "CCTV", "FP", "KAS", "KB", "KBL", "KBM",
                                "MC", "MI", "MM", "MS", "MSN", "PP", "SCR", "SOFT", "SP", "STAB", "UFD"));
                manualHierarchy.put("HPTB", List.of("HPTB", "HP", "TAB"));

                // 2. Prepare buckets for aggregation
                Map<String, StockSummaryRowResponse> rowMap = new LinkedHashMap<>();
                for (String parentName : manualHierarchy.keySet()) {
                        rowMap.put(parentName, StockSummaryRowResponse.builder()
                                        .nama(parentName)
                                        .stok(BigDecimal.ZERO)
                                        .presentase(BigDecimal.ZERO)
                                        .children(new ArrayList<>())
                                        .build());
                }

                // 3. Populate buckets from raw data
                for (Object[] res : rawResults) {
                        String kategoriItemcode = (String) res[0];
                        // String kategoriNama = (String) res[1];
                        BigDecimal grandTotalNode = (BigDecimal) res[2];

                        // Match kategoriItemcode to our manualHierarchy
                        String targetParent = null;
                        if (kategoriItemcode != null) {
                                final String normalizedCode = kategoriItemcode.trim().toUpperCase();
                                for (Map.Entry<String, List<String>> entry : manualHierarchy.entrySet()) {
                                        if (entry.getValue().stream()
                                                        .anyMatch(code -> code.equalsIgnoreCase(normalizedCode))) {
                                                targetParent = entry.getKey();
                                                break;
                                        }
                                }
                        }

                        if (targetParent != null) {
                                StockSummaryRowResponse parent = rowMap.get(targetParent);
                                parent.setStok(parent.getStok().add(grandTotalNode));

                                // If the parent is the same as the child (e.g. 2ND, PROJEKTOR, UPS, etc.), we
                                // don't necessarily need children
                                // unless it is one of those with multiple codes.
                                // But based on the image, even single categories can be shown.
                                // Image shows 2ND, PROJEKTOR, UPS as main rows without sub-items below them if
                                // they only have one.
                                // However, they exhibit specialized behavior: BRANDED has PCAIO, PCBU, PCMINI
                                // INDENTED.

                                List<String> subCategoryCodes = manualHierarchy.get(targetParent);
                                if (subCategoryCodes.size() > 1 || (subCategoryCodes.size() == 1
                                                && !subCategoryCodes.get(0).equals(targetParent))) {
                                        // Add as a child if it's part of a group
                                        BigDecimal childPercentage = totalAll.compareTo(BigDecimal.ZERO) > 0
                                                        ? grandTotalNode.multiply(new BigDecimal(100)).divide(totalAll,
                                                                        2, RoundingMode.HALF_UP)
                                                        : BigDecimal.ZERO;

                                        parent.getChildren().add(StockSummaryRowResponse.builder()
                                                        .nama(kategoriItemcode) // Use the itemcode as label (matches
                                                                                // labels like PROC, MB, VGA in image)
                                                        .stok(grandTotalNode)
                                                        .presentase(childPercentage)
                                                        .children(new ArrayList<>())
                                                        .build());
                                }
                        }
                }

                // 4. Finalize Parent Percentages and return list
                List<StockSummaryRowResponse> finalData = new ArrayList<>();
                for (StockSummaryRowResponse parent : rowMap.values()) {
                        BigDecimal parentPercentage = totalAll.compareTo(BigDecimal.ZERO) > 0
                                        ? parent.getStok().multiply(new BigDecimal(100)).divide(totalAll, 2,
                                                        RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;
                        parent.setPresentase(parentPercentage);
                        finalData.add(parent);
                }

                // 5. Add TOTAL row
                finalData.add(StockSummaryRowResponse.builder()
                                .nama("TOTAL")
                                .stok(totalAll)
                                .presentase(new BigDecimal(100))
                                .children(new ArrayList<>())
                                .build());

                return finalData;
        }

        public Stock getSingleStockDetail(Long id, String username) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
                Role role = user.getRole();
                // HPP & Modal Final hanya untuk SPV, ADMIN, dan MANAGER.
                boolean canSeeCost = canViewCost(role);

                Stock stock = stockRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Stock not found with id: " + id));

                LocalDate lastSalesDate = salesRepository.findLatestDocDateByItemName(stock.getItemName());
                stock.setLastSalesDate(lastSalesDate);

                purchaseRepository.findLatestPurchaseByItemName(stock.getItemName()).ifPresent(p -> {
                        stock.setLastPurchaseDate(p.getDocDate());
                        stock.setLastPurchasePrice(p.getPrice());
                        stock.setParName(MigrationService.cleanDistributorName(p.getParName()));
                });

                // Fallback: jika tidak ditemukan di purchases atau masih "PERSEDIAAN AWAL", coba cari di old_purchase
                if (stock.getParName() == null || "PERSEDIAAN AWAL".equalsIgnoreCase(stock.getParName())) {
                        oldPurchaseRepository.findLatestPurchaseByItemName(stock.getItemName()).ifPresent(op -> {
                                stock.setLastPurchaseDate(op.getDocDate());
                                stock.setLastPurchasePrice(op.getPrice());
                                stock.setParName(MigrationService.cleanDistributorName(op.getParName()));
                        });
                }

                // Fetch from pricelist using normalized name
                String normalizedName = com.stok.anandam.store.util.NormalizationUtil
                                .normalizeItemName(stock.getItemName());
                pricelistRepository.findByNormalizedItemName(normalizedName).ifPresent(p -> {
                        stock.setSpesifikasi(p.getSpesifikasi());
                        stock.setModal(p.getModal());
                        stock.setFinalPricelist(p.getFinalPricelist());
                });

                if (!canSeeCost) {
                        stock.setHargaHpp(null);
                        stock.setModal(null);
                        stock.setGrandTotal(null);
                        stock.setLastPurchasePrice(null);
                }

                // Tentukan PPN / NON PPN dari distributor pembelian terakhir item ini
                stock.setIsPpn(resolveIsPpn(stock.getParName(), buildDistributorMap(), stock.getIsPpn()));

                return stock;
        }

        public Page<Stock> getAllStocks(int page, int size, String sortBy, String direction, String search) {
                Sort.Direction sortDirection = direction.equalsIgnoreCase("desc") ? Sort.Direction.DESC
                                : Sort.Direction.ASC;
                Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

                return stockRepository.findByFilters(search, null, null, pageable);
        }

// ─── STOK PER BADAN (redistribusi deficit ke ANC) ──────────────

        private static final List<String> BADAN_LIST = List.of("ANC", "PDB", "MGC", "GBH", "SSS", "SGI");

        /**
         * Ambil stok per badan: untuk setiap (badan, dep_code, item_code) hitung
         * total_purchase_qty - total_sales_qty. Lalu terapkan redistribusi:
         * deficit badan non-ANC diambil dari stok ANC (gudang pusat) per item_code.
         * Hasil dikelompokkan per badan (selalu 6 badan tampil walau kosong).
         *
         * Mirip dengan StokService.getStokPerBadan() di portal-tim-project.
         */
        public List<StokBadanGroupResponse> getStokPerBadan() {
            List<Object[]> rawRows = stockRepository.findStokPerBadanRaw();

            // ── Map ke objek ──
            List<StokBadanItem> items = new ArrayList<>();
            for (Object[] row : rawRows) {
                String badan = (String) row[0];
                String depCode = (String) row[1];
                String depName = (String) row[2];
                String itemCode = (String) row[3];
                String itemName = (String) row[4];
                Object rawStokQty = row[5];
                Object rawLineCount = row[6];

                int stokQty = rawStokQty instanceof Number ? ((Number) rawStokQty).intValue() : 0;
                int lineCount = rawLineCount instanceof Number ? ((Number) rawLineCount).intValue() : 0;

                items.add(StokBadanItem.builder()
                        .badan(badan)
                        .depCode(depCode)
                        .depName(depName)
                        .itemCode(itemCode)
                        .itemName(itemName)
                        .stokQty(stokQty)
                        .lineCount(lineCount)
                        .build());
            }

            // ── Redistribusi stok: deficit non-ANC diambil dari ANC ──
            // 1. Kelompokkan per item_code
            Map<String, List<StokBadanItem>> itemGroups = new LinkedHashMap<>();
            for (StokBadanItem item : items) {
                itemGroups.computeIfAbsent(item.getItemCode(), k -> new ArrayList<>()).add(item);
            }

            // 2. Untuk setiap grup item_code
            for (List<StokBadanItem> group : itemGroups.values()) {
                // Hitung total deficit non-ANC
                int deficit = 0;
                for (StokBadanItem item : group) {
                    if (!"ANC".equals(item.getBadan()) && item.getStokQty() < 0) {
                        deficit += Math.abs(item.getStokQty());
                    }
                }
                if (deficit == 0) continue;

                // Kumpulkan baris ANC yang stok positif, urut dari terbesar
                List<StokBadanItem> ancRows = group.stream()
                        .filter(r -> "ANC".equals(r.getBadan()) && r.getStokQty() > 0)
                        .sorted((a, b) -> b.getStokQty().compareTo(a.getStokQty()))
                        .collect(Collectors.toList());

                // Kurangi ANC satu per satu sampai deficit habis
                int remaining = deficit;
                for (StokBadanItem ancRow : ancRows) {
                    if (remaining <= 0) break;
                    int deduction = Math.min(remaining, ancRow.getStokQty());
                    ancRow.setStokQty(ancRow.getStokQty() - deduction);
                    remaining -= deduction;
                }

                // Nol-kan non-ANC yang minus
                for (StokBadanItem item : group) {
                    if (!"ANC".equals(item.getBadan()) && item.getStokQty() < 0) {
                        item.setStokQty(0);
                    }
                }
            }

            // 3. Filter stok != 0
            List<StokBadanItem> filtered = items.stream()
                    .filter(r -> r.getStokQty() != 0)
                    .collect(Collectors.toList());

            // 4. Kelompokkan per badan (6 badan selalu tampil)
            List<StokBadanGroupResponse> result = new ArrayList<>();
            for (String badan : BADAN_LIST) {
                List<StokBadanItem> badanItems = filtered.stream()
                        .filter(r -> badan.equals(r.getBadan()))
                        .collect(Collectors.toList());
                int totalQty = badanItems.stream().mapToInt(StokBadanItem::getStokQty).sum();
                result.add(StokBadanGroupResponse.builder()
                        .badan(badan)
                        .totalItems(badanItems.size())
                        .totalQty(totalQty)
                        .items(badanItems)
                        .build());
            }

            return result;
        }
/**
         * Ambil stok per badan untuk SATU item tertentu (berdasarkan itemCode).
         * Mengembalikan map: badan -> stokQty untuk 6 badan (ANC,PDB,MGC,GBH,SSS,SGI).
         */
        public Map<String, Integer> getStokPerBadanByItemCode(String itemCode) {
            // Gunakan query yang sama dengan findStokPerBadanRaw() tapi filter by item_code
            String sql = """
                    WITH pur AS (
                      SELECT p.id, p.par_name, p.doc_no_p, p.doc_date, p.dep_code, p.dep_name,
                            p.item_code, p.item_name, p.qty, p.last_synced,
                            CASE
                              WHEN p.par_name ~* '\\\\mSGI\\\\M' THEN 'SGI'
                              WHEN p.par_name ~* '\\\\mSSS\\\\M' THEN 'SSS'
                              WHEN p.par_name ~* '\\\\mGBH\\\\M' THEN 'GBH'
                              WHEN p.par_name ~* '\\\\mMGC\\\\M' THEN 'MGC'
                              WHEN p.par_name ~* '\\\\mPDB\\\\M' THEN 'PDB'
                              ELSE 'ANC'
                            END AS badan
                      FROM purchases p
                      WHERE LOWER(TRIM(p.item_code)) = LOWER(TRIM(?))
                    ),
                    sls AS (
                      SELECT s.ite_code, s.dep_code, s.dep_name, s.item_name, s.qty,
                            CASE
                              WHEN s.code ~* '\\\\mSGI\\\\M' THEN 'SGI'
                              WHEN s.code ~* '\\\\mSSS\\\\M' THEN 'SSS'
                              WHEN s.code ~* '\\\\mGBH\\\\M' THEN 'GBH'
                              WHEN s.code ~* '\\\\mMGC\\\\M' THEN 'MGC'
                              WHEN s.code ~* '\\\\mPDB\\\\M' THEN 'PDB'
                              ELSE 'ANC'
                            END AS badan
                      FROM sales s
                      WHERE LOWER(TRIM(s.ite_code)) = LOWER(TRIM(?))
                    ),
                    pur_agg AS (
                      SELECT badan, dep_code, dep_name, item_code, item_name,
                             SUM(qty) AS total_pur_qty,
                             COUNT(*) AS purchase_lines
                      FROM pur
                      GROUP BY badan, dep_code, dep_name, item_code, item_name
                    ),
                    sls_agg AS (
                      SELECT badan, dep_code, ite_code,
                             MIN(dep_name) AS dep_name,
                             MIN(item_name) AS item_name,
                             SUM(qty) AS total_sls_qty,
                             COUNT(*) AS sales_lines
                      FROM sls
                      GROUP BY badan, dep_code, ite_code
                    )
                    SELECT
                      COALESCE(pa.badan, sa.badan) AS badan,
                      COALESCE(pa.dep_code, sa.dep_code) AS dep_code,
                      COALESCE(pa.dep_name, sa.dep_name) AS dep_name,
                      COALESCE(pa.item_code, sa.ite_code) AS item_code,
                      COALESCE(pa.item_name, sa.item_name) AS item_name,
                      (COALESCE(pa.total_pur_qty, 0) - COALESCE(sa.total_sls_qty, 0))::int AS stok_qty,
                      (COALESCE(pa.purchase_lines, 0) + COALESCE(sa.sales_lines, 0))::int AS line_count
                    FROM pur_agg pa
                    FULL OUTER JOIN sls_agg sa
                      ON sa.badan = pa.badan
                     AND sa.dep_code = pa.dep_code
                     AND sa.ite_code = pa.item_code
                    WHERE (COALESCE(pa.total_pur_qty, 0) - COALESCE(sa.total_sls_qty, 0)) <> 0
                    ORDER BY badan, stok_qty DESC, item_name ASC
                    """;
            List<Map<String, Object>> rows = pgJdbcTemplate.queryForList(sql, itemCode, itemCode);
            Map<String, Integer> result = new LinkedHashMap<>();
            for (String badan : BADAN_LIST) {
                result.put(badan, 0);
            }
            for (Map<String, Object> row : rows) {
                String badan = (String) row.get("badan");
                if (badan != null && result.containsKey(badan)) {
                    result.put(badan, result.get(badan) + ((Number) row.get("stok_qty")).intValue());
                }
            }
            return result;
        }
        // ─── Helper PPN / NON PPN ─────────────────────────────────────────
        // ─── Helper PPN / NON PPN ─────────────────────────────────────────
        // Map nama distributor (ternormalisasi) -> entitas distributor
        private Map<String, Distributor> buildDistributorMap() {
                Map<String, Distributor> map = new HashMap<>();
                for (Distributor d : distributorRepository.findAll()) {
                        if (d != null && d.getNamaDistributor() != null) {
                                map.put(normalizeForMatch(d.getNamaDistributor()), d);
                        }
                }
                return map;
        }

        private String normalizeForMatch(String s) {
                if (s == null) return "";
                String cleaned = MigrationService.cleanDistributorName(s.trim().toLowerCase());
                return cleaned;
        }

        // Tentukan is_ppn item:
        //  - cocokkan par_name pembelian terakhir item -> distributor.tipe_pajak
        //  - jika distributor tidak ditemukan atau tipe_pajaknya null, return null
        //    (belum diketahui) — abaikan nilai stored dari DB yang mungkin sudah terlanjur salah
        private Boolean resolveIsPpn(String parName, Map<String, Distributor> distributorByName, Boolean stored) {
                if (parName != null) {
                        Distributor d = distributorByName.get(normalizeForMatch(parName));
                        if (d != null && d.getTipePajak() != null) {
                                return "PPN".equalsIgnoreCase(d.getTipePajak());
                        }
                }
                // Distributor tidak ditemukan atau tipe_pajak-nya null -> belum diketahui
                return null;
        }
}