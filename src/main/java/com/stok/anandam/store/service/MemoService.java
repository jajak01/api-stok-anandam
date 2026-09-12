package com.stok.anandam.store.service;

import com.stok.anandam.store.core.postgres.model.*;
import com.stok.anandam.store.core.postgres.model.enums.MemoStatus;
import com.stok.anandam.store.core.postgres.model.enums.StatusJadwal;
import com.stok.anandam.store.core.postgres.model.enums.TipeTugas;
import com.stok.anandam.store.core.postgres.repository.*;
import com.stok.anandam.store.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate; 
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

@Service
@RequiredArgsConstructor
public class MemoService {

    private final MemoRepository memoRepository;
    private final MemoLogRepository memoLogRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private void sendMemoRefreshSignal() {
        try {
            messagingTemplate.convertAndSend("/topic/memos", "REFRESH");
        } catch (Exception e) {
            // Jangan biarkan error websocket menggagalkan transaksi utama
            System.err.println("Gagal mengirim sinyal WebSocket: " + e.getMessage());
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        
        // 1. Cek tipe file (MIME type)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File harus berupa gambar (JPEG/PNG)");
        }
        
        // 2. Cek ekstensi
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            int lastIndex = fileName.lastIndexOf(".");
            if (lastIndex == -1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File tidak memiliki ekstensi");
            }
            String ext = fileName.substring(lastIndex + 1).toLowerCase();
            if (!java.util.List.of("jpg", "jpeg", "png", "webp").contains(ext)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ekstensi file tidak didukung. Gunakan JPG, PNG, atau WEBP.");
            }
        }
        
        // 3. Cek ukuran (maks 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ukuran file maksimal adalah 5MB");
        }
    }
    private final PenjadwalanKonfirmasiRepository penjadwalanRepo;
    private final MemoItemRepository memoItemRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PelangganMybizRepository pelangganMybizRepository;
    private final KodeposRepository kodeposRepository;
    private final SalesRepository salesRepository;

    private final FileService fileService;
    private final ObjectMapper objectMapper;
    private final ActivityLogService activityLogService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @Transactional(readOnly = true)
    public WebResponse<List<MemoDetailResponse>> getListMemoByStatus(MemoStatus status, String username) {
        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        List<Memo> memos;
        if (status != null) {
            memos = memoRepository.findByStatusAkhirOrderByCreatedAtDesc(status);
        } else {
            memos = memoRepository.findAllByOrderByCreatedAtDesc();
        }

        // ROLE-BASED & SCOPE-BASED FILTERING
        String roleName = aktor.getRole() != null ? aktor.getRole().name() : "";
        String userEmpCode = aktor.getEmployeeCode(); 
        java.util.Set<String> empScope = aktor.getEmpCodeAccess() != null ? aktor.getEmpCodeAccess() : java.util.Collections.emptySet();

        if (aktor.getRole() == Role.SPV_MARKETING) {
            // SPV MARKETING can see all marketing memos
            memos = memos.stream()
                    .filter(m -> m.getMarketingEmpCode() != null || 
                            (m.getCreator() != null && m.getCreator().getRole() != null && 
                             (m.getCreator().getRole().name().startsWith("MARKETING") || m.getCreator().getRole() == Role.SPV_MARKETING)))
                    .collect(Collectors.toList());
        } else if (roleName.startsWith("MARKETING_") || "MARKETING".equals(roleName)) {
            List<UUID> assignedMemoIds = penjadwalanRepo.findByPersonelIdAndDeletedAtIsNull(aktor.getId())
                    .stream()
                    .filter(t -> t.getMemo() != null)
                    .map(t -> t.getMemo().getId())
                    .collect(Collectors.toList());

            memos = memos.stream()
                    .filter(m -> {
                        boolean isOwner = (userEmpCode != null && !userEmpCode.isBlank() && userEmpCode.equalsIgnoreCase(m.getMarketingEmpCode()))
                                || (m.getMarketingEmpCode() != null && empScope.stream().anyMatch(e -> e.equalsIgnoreCase(m.getMarketingEmpCode())));
                        boolean isCreator = m.getCreator() != null && m.getCreator().getId().equals(aktor.getId());
                        boolean isAssigned = assignedMemoIds.contains(m.getId());
                        boolean isSameRole = (m.getCreator() != null && m.getCreator().getRole() != null && m.getCreator().getRole() == aktor.getRole())
                                || (m.getMarketing() != null && m.getMarketing().getRole() != null && m.getMarketing().getRole() == aktor.getRole());

                        return isSameRole || isOwner || isCreator || isAssigned;
                    })
                    .collect(Collectors.toList());
        } else if ("GUDANG".equals(roleName) || "SPV_GUDANG".equals(roleName)) {
            memos = memos.stream()
                    .filter(m -> 
                        m.getStatusAkhir() == MemoStatus.PENDING ||
                        m.getStatusAkhir() == MemoStatus.MENUNGGU_PERSETUJUAN ||
                        m.getStatusAkhir() == MemoStatus.DISETUJUI ||
                        m.getStatusAkhir() == MemoStatus.DITOLAK ||
                        m.getStatusAkhir() == MemoStatus.MENUNGGU_GUDANG ||
                        m.getStatusAkhir() == MemoStatus.MENUNGGU_NOTA ||
                        m.getStatusAkhir() == MemoStatus.KENDALA_BARANG ||
                        m.getStatusAkhir() == MemoStatus.MENUNGGU_TEKNISI ||
                        m.getStatusAkhir() == MemoStatus.PROSES_TEKNISI ||
                        m.getStatusAkhir() == MemoStatus.BUFFER_ZONE ||
                        m.getStatusAkhir() == MemoStatus.MENUNGGU_PENGIRIMAN ||
                        m.getStatusAkhir() == MemoStatus.DALAM_PENGIRIMAN ||
                        m.getStatusAkhir() == MemoStatus.TERKIRIM_SEBAGIAN ||
                        m.getStatusAkhir() == MemoStatus.SELESAI
                    )
                    .collect(Collectors.toList());
        } else if ("TEKNISI".equals(roleName) || "SPV_TEKNISI".equals(roleName)) {
            memos = memos.stream()
                    .filter(m -> 
                        m.getStatusAkhir() == MemoStatus.MENUNGGU_TEKNISI ||
                        m.getStatusAkhir() == MemoStatus.PROSES_TEKNISI
                    )
                    .collect(Collectors.toList());
        } else if ("NOTA".equals(roleName)) {
            memos = memos.stream()
                    .filter(m -> m.getStatusAkhir() == MemoStatus.MENUNGGU_NOTA)
                    .collect(Collectors.toList());
        } else if ("DELIVERY".equals(roleName)) {
            List<UUID> assignedMemoIds = penjadwalanRepo.findByPersonelIdAndDeletedAtIsNull(aktor.getId())
                    .stream()
                    .filter(t -> t.getMemo() != null)
                    .map(t -> t.getMemo().getId())
                    .collect(Collectors.toList());

            memos = memos.stream()
                    .filter(m -> assignedMemoIds.contains(m.getId()))
                    .collect(Collectors.toList());
        }

        List<MemoDetailResponse> responses = mapToDetailResponses(memos);

        return WebResponse.<List<MemoDetailResponse>>builder()
                .status(200)
                .message("Success")
                .data(responses)
                .build();
    }

    /**
     * Mengambil list memo berdasarkan beberapa status sekaligus (1 query IN clause).
     * Digunakan oleh halaman Pengiriman untuk menghindari fetch semua memo.
     */
    @Transactional(readOnly = true)
    public WebResponse<List<MemoDetailResponse>> getListMemoByStatuses(List<MemoStatus> statuses, String username) {
        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        List<Memo> memos;
        if (statuses == null || statuses.isEmpty()) {
            memos = memoRepository.findAllByOrderByCreatedAtDesc();
        } else {
            memos = memoRepository.findByStatusAkhirInOrderByCreatedAtDesc(statuses);
        }

        // Terapkan role-based filtering yang sama seperti getListMemoByStatus
        String roleName = aktor.getRole() != null ? aktor.getRole().name() : "";
        String userEmpCode = aktor.getEmployeeCode();
        java.util.Set<String> empScope = aktor.getEmpCodeAccess() != null ? aktor.getEmpCodeAccess() : java.util.Collections.emptySet();

        if (aktor.getRole() == Role.SPV_MARKETING) {
            memos = memos.stream()
                    .filter(m -> m.getMarketingEmpCode() != null || 
                            (m.getCreator() != null && m.getCreator().getRole() != null && 
                             (m.getCreator().getRole().name().startsWith("MARKETING") || m.getCreator().getRole() == Role.SPV_MARKETING)))
                    .collect(Collectors.toList());
        } else if (roleName.startsWith("MARKETING_") || "MARKETING".equals(roleName)) {
            List<UUID> assignedMemoIds = penjadwalanRepo.findByPersonelIdAndDeletedAtIsNull(aktor.getId())
                    .stream()
                    .filter(t -> t.getMemo() != null)
                    .map(t -> t.getMemo().getId())
                    .collect(Collectors.toList());

            memos = memos.stream()
                    .filter(m -> {
                        boolean isOwner = (userEmpCode != null && !userEmpCode.isBlank() && userEmpCode.equalsIgnoreCase(m.getMarketingEmpCode()))
                                || (m.getMarketingEmpCode() != null && empScope.stream().anyMatch(e -> e.equalsIgnoreCase(m.getMarketingEmpCode())));
                        boolean isCreator = m.getCreator() != null && m.getCreator().getId().equals(aktor.getId());
                        boolean isAssigned = assignedMemoIds.contains(m.getId());
                        boolean isSameRole = (m.getCreator() != null && m.getCreator().getRole() != null && m.getCreator().getRole() == aktor.getRole())
                                || (m.getMarketing() != null && m.getMarketing().getRole() != null && m.getMarketing().getRole() == aktor.getRole());
                        return isSameRole || isOwner || isCreator || isAssigned;
                    })
                    .collect(Collectors.toList());
        } else if ("DELIVERY".equals(roleName)) {
            List<UUID> assignedMemoIds = penjadwalanRepo.findByPersonelIdAndDeletedAtIsNull(aktor.getId())
                    .stream()
                    .filter(t -> t.getMemo() != null)
                    .map(t -> t.getMemo().getId())
                    .collect(Collectors.toList());
            memos = memos.stream()
                    .filter(m -> assignedMemoIds.contains(m.getId()))
                    .collect(Collectors.toList());
        }
        // ADMIN, MANAGER, GUDANG, dll → tidak ada filter tambahan

        List<MemoDetailResponse> responses = mapToDetailResponses(memos);
        return WebResponse.<List<MemoDetailResponse>>builder()
                .status(200)
                .message("Success")
                .data(responses)
                .build();
    }

    @Transactional(readOnly = true)
    public WebResponse<java.util.Map<String, Long>> getMemoCounts(String username) {
        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        String roleName = aktor.getRole() != null ? aktor.getRole().name() : "";
        String userEmpCode = aktor.getEmployeeCode();
        java.util.Set<String> empScope = aktor.getEmpCodeAccess() != null ? aktor.getEmpCodeAccess() : java.util.Collections.emptySet();
        List<String> empScopeList = new java.util.ArrayList<>(empScope);
        boolean hasEmpScope = !empScopeList.isEmpty();
        List<Object[]> rawCounts = new java.util.ArrayList<>();

        if (aktor.getRole() == Role.SPV_MARKETING) {
            List<Role> marketingRoles = java.util.Arrays.asList(
                    Role.MARKETING, Role.MARKETING_TOKO, Role.MARKETING_PROJECT, 
                    Role.MARKETING_DISTRIBUSI, Role.MARKETING_ONLINE, Role.SPV_MARKETING);
            rawCounts = memoRepository.countStatusesForSpvMarketing(marketingRoles);
        } else if (roleName.startsWith("MARKETING_") || "MARKETING".equals(roleName)) {
            List<UUID> assignedMemoIds = penjadwalanRepo.findByPersonelIdAndDeletedAtIsNull(aktor.getId())
                    .stream()
                    .filter(t -> t.getMemo() != null)
                    .map(t -> t.getMemo().getId())
                    .collect(Collectors.toList());

            if (assignedMemoIds.isEmpty()) {
                rawCounts = memoRepository.countStatusesForMarketingWithoutAssigned(userEmpCode, hasEmpScope, empScopeList, aktor.getId(), aktor.getRole());
            } else {
                rawCounts = memoRepository.countStatusesForMarketingWithAssigned(userEmpCode, hasEmpScope, empScopeList, aktor.getId(), assignedMemoIds, aktor.getRole());
            }
        } else if ("DELIVERY".equals(roleName)) {
            List<UUID> assignedMemoIds = penjadwalanRepo.findByPersonelIdAndDeletedAtIsNull(aktor.getId())
                    .stream()
                    .filter(t -> t.getMemo() != null)
                    .map(t -> t.getMemo().getId())
                    .collect(Collectors.toList());

            if (!assignedMemoIds.isEmpty()) {
                rawCounts = memoRepository.countStatusesForDelivery(assignedMemoIds);
            }
        } else if ("TEKNISI".equals(roleName) || "SPV_TEKNISI".equals(roleName)) {
            rawCounts = memoRepository.countStatusesByStatusList(List.of(
                    MemoStatus.MENUNGGU_TEKNISI,
                    MemoStatus.PROSES_TEKNISI
            ));
        } else if ("GUDANG".equals(roleName) || "SPV_GUDANG".equals(roleName)) {
            rawCounts = memoRepository.countStatusesByStatusList(List.of(
                    MemoStatus.PENDING,
                    MemoStatus.MENUNGGU_PERSETUJUAN,
                    MemoStatus.DISETUJUI,
                    MemoStatus.DITOLAK,
                    MemoStatus.MENUNGGU_GUDANG,
                    MemoStatus.MENUNGGU_NOTA,
                    MemoStatus.KENDALA_BARANG,
                    MemoStatus.MENUNGGU_TEKNISI,
                    MemoStatus.PROSES_TEKNISI,
                    MemoStatus.BUFFER_ZONE,
                    MemoStatus.MENUNGGU_PENGIRIMAN,
                    MemoStatus.DALAM_PENGIRIMAN,
                    MemoStatus.TERKIRIM_SEBAGIAN,
                    MemoStatus.SELESAI
            ));
        } else if ("NOTA".equals(roleName)) {
            rawCounts = memoRepository.countStatusesByStatusList(List.of(MemoStatus.MENUNGGU_NOTA));
        } else {
            rawCounts = memoRepository.countAllStatuses();
        }

        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        for (MemoStatus status : MemoStatus.values()) {
            counts.put(status.name(), 0L);
        }

        for (Object[] res : rawCounts) {
            MemoStatus status = (MemoStatus) res[0];
            Long count = (Long) res[1];
            if (status != null) {
                counts.put(status.name(), count);
            }
        }

        return WebResponse.<java.util.Map<String, Long>>builder()
                .status(200)
                .message("Success Fetch Status Counts")
                .data(counts)
                .build();
    }

    @Transactional(readOnly = true)
    public WebResponse<MemoDetailResponse> getMemoDetail(UUID memoId, String username) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        if (!canUserReadMemo(memo, aktor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses ke memo ini");
        }

        return WebResponse.<MemoDetailResponse>builder()
                .status(200)
                .message("Success")
                .data(mapToDetailResponse(memo))
                .build();
    }

    public boolean canUserReadMemo(Memo memo, User aktor) {
        if (aktor == null || memo == null) return false;

        // Only ADMIN and MANAGER have access to all memos
        if (aktor.getRole() == Role.ADMIN || aktor.getRole() == Role.MANAGER) {
            return true;
        }

        // Creator can always read
        if (memo.getCreator() != null && memo.getCreator().getId().equals(aktor.getId())) {
            return true;
        }

        // Same role can read memo
        if (memo.getCreator() != null && memo.getCreator().getRole() != null && memo.getCreator().getRole() == aktor.getRole()) {
            return true;
        }
        if (memo.getMarketing() != null && memo.getMarketing().getRole() != null && memo.getMarketing().getRole() == aktor.getRole()) {
            return true;
        }

        // SPV_MARKETING can read any marketing memo
        if (aktor.getRole() == Role.SPV_MARKETING) {
            return memo.getMarketingEmpCode() != null || 
                   (memo.getCreator() != null && memo.getCreator().getRole() != null && 
                    (memo.getCreator().getRole().name().startsWith("MARKETING") || memo.getCreator().getRole() == Role.SPV_MARKETING));
        }

        // Owner by marketing emp code or emp code scope
        String userEmpCode = aktor.getEmployeeCode();
        java.util.Set<String> empScope = aktor.getEmpCodeAccess() != null ? aktor.getEmpCodeAccess() : java.util.Collections.emptySet();
        if (memo.getMarketingEmpCode() != null) {
            if (userEmpCode != null && !userEmpCode.isBlank() && memo.getMarketingEmpCode().equalsIgnoreCase(userEmpCode)) {
                return true;
            }
            if (empScope.stream().anyMatch(e -> e.equalsIgnoreCase(memo.getMarketingEmpCode()))) {
                return true;
            }
        }

        // Delivery personnel assigned to deliver this memo
        if (aktor.getRole() == Role.DELIVERY) {
            List<PenjadwalanKonfirmasi> assignedTasks = penjadwalanRepo.findByPersonelIdAndDeletedAtIsNullOrderByCreatedAtDesc(aktor.getId());
            return assignedTasks.stream().anyMatch(t -> t.getMemo() != null && t.getMemo().getId().equals(memo.getId()));
        }

        // Warehouse personnel processing warehouse statuses
        if (aktor.getRole() == Role.GUDANG || aktor.getRole() == Role.SPV_GUDANG) {
            MemoStatus st = memo.getStatusAkhir();
            return st != null && (
                st == MemoStatus.PENDING ||
                st == MemoStatus.MENUNGGU_PERSETUJUAN ||
                st == MemoStatus.DISETUJUI ||
                st == MemoStatus.DITOLAK ||
                st == MemoStatus.MENUNGGU_GUDANG ||
                st == MemoStatus.MENUNGGU_NOTA ||
                st == MemoStatus.KENDALA_BARANG ||
                st == MemoStatus.MENUNGGU_TEKNISI ||
                st == MemoStatus.PROSES_TEKNISI ||
                st == MemoStatus.BUFFER_ZONE ||
                st == MemoStatus.MENUNGGU_PENGIRIMAN ||
                st == MemoStatus.DALAM_PENGIRIMAN ||
                st == MemoStatus.TERKIRIM_SEBAGIAN ||
                st == MemoStatus.SELESAI
            );
        }

        // Technician processing technician statuses
        if (aktor.getRole() == Role.TEKNISI || aktor.getRole() == Role.SPV_TEKNISI) {
            MemoStatus st = memo.getStatusAkhir();
            return st == MemoStatus.MENUNGGU_TEKNISI || st == MemoStatus.PROSES_TEKNISI;
        }

        // Nota processing nota status
        if (aktor.getRole() == Role.NOTA) {
            return memo.getStatusAkhir() == MemoStatus.MENUNGGU_NOTA;
        }

        // Different role cannot read
        return false;
    }

    private List<MemoDetailResponse> mapToDetailResponses(List<Memo> memos) {
        if (memos.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<UUID> memoIds = memos.stream().map(Memo::getId).collect(Collectors.toList());

        // 1. Bulk fetch all items in exactly 1 query
        List<MemoItem> allItems = memoItemRepository.findByMemoIdIn(memoIds);
        java.util.Map<UUID, List<MemoItem>> itemsByMemoId = allItems.stream()
                .collect(Collectors.groupingBy(item -> item.getMemo().getId()));

        // 2. Bulk fetch all penjadwalans in exactly 1 query
        List<PenjadwalanKonfirmasi> allPenjadwalans = penjadwalanRepo.findByMemo_IdInAndDeletedAtIsNull(memoIds);
        java.util.Map<UUID, List<PenjadwalanKonfirmasi>> penjadwalansByMemoId = allPenjadwalans.stream()
                .collect(Collectors.groupingBy(p -> p.getMemo().getId()));

        // 3. Gather all personel IDs to resolve them in 1 bulk query (eliminates N+1 UserRepository)
        List<Long> personelIds = allPenjadwalans.stream()
                .map(PenjadwalanKonfirmasi::getPersonelId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        java.util.Map<Long, User> personelMap = new java.util.HashMap<>();
        if (!personelIds.isEmpty()) {
            List<User> personels = userRepository.findAllById(personelIds);
            for (User p : personels) {
                personelMap.put(p.getId(), p);
            }
        }

        // 4. Bulk fetch all revisedFrom memos in exactly 1 query (eliminates N+1 MemoRepository loop)
        List<UUID> revisedFromIds = memos.stream()
                .map(Memo::getRevisedFromId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        java.util.Map<UUID, String> revisedFromMap = new java.util.HashMap<>();
        if (!revisedFromIds.isEmpty()) {
            memoRepository.findAllById(revisedFromIds)
                    .forEach(m -> revisedFromMap.put(m.getId(), m.getNomorMemo()));
        }

        // 5. Bulk fetch all distinct kodepos in exactly 1 query (eliminates N+1 KodeposRepository loop)
        List<String> distinctKodePosList = memos.stream()
                .map(Memo::getKodePos)
                .filter(k -> k != null && !k.isBlank())
                .distinct()
                .collect(Collectors.toList());
        java.util.Map<String, com.stok.anandam.store.core.postgres.model.Kodepos> kodeposMap = new java.util.HashMap<>();
        if (!distinctKodePosList.isEmpty()) {
            kodeposRepository.findByKodePosIn(distinctKodePosList)
                    .forEach(kp -> kodeposMap.putIfAbsent(kp.getKodePos(), kp));
        }

        List<MemoDetailResponse> responses = new java.util.ArrayList<>();

        for (Memo memo : memos) {
            List<MemoItem> items = itemsByMemoId.getOrDefault(memo.getId(), new java.util.ArrayList<>());
            List<MemoItemResponse> itemResponses = items.stream()
                    .map(item -> MemoItemResponse.builder()
                            .id(item.getId())
                            .namaBarang(item.getNamaBarang())
                            .qty(item.getQty() != null ? item.getQty() : 0)
                            .qtyShipped(item.getQtyShipped() != null ? item.getQtyShipped() : 0)
                            .qtyRemaining((item.getQty() != null ? item.getQty() : 0) - (item.getQtyShipped() != null ? item.getQtyShipped() : 0))
                            .hargaSatuan(item.getHargaSatuan())
                            .subtotal(item.getSubtotal())
                            .catatanGudang(item.getCatatanGudang())
                            .itemStatus(item.getItemStatus())
                            .status(item.getStatus())
                            .build()
                    ).collect(Collectors.toList());

            List<PenjadwalanKonfirmasi> penjadwalans = penjadwalansByMemoId.getOrDefault(memo.getId(), new java.util.ArrayList<>());
            List<PenjadwalanResponse> penjadwalanResponses = penjadwalans.stream()
                    .map(p -> {
                        PenjadwalanResponse.PenjadwalanResponseBuilder pBuilder = PenjadwalanResponse.builder()
                                .id(p.getId())
                                .memoId(p.getMemo().getId())
                                .nomorMemo(p.getMemo().getNomorMemo())
                                .tipeTugas(p.getTipeTugas())
                                .statusJadwal(p.getStatusJadwal())
                                .alamatLengkap(p.getAlamatLengkap())
                                .alamatMaps(p.getAlamatMaps())
                                .idKodepos(p.getIdKodepos())
                                .estimasiWaktu(p.getEstimasiWaktu())
                                .catatan(p.getCatatan())
                                .tanggalJadwal(p.getTanggalJadwal() != null ? p.getTanggalJadwal().format(DATE_FORMATTER) : null)
                                .personelId(p.getPersonelId())
                                .latitude(p.getLatitude())
                                .longitude(p.getLongitude())
                                .kecamatan(p.getKecamatan())
                                .desaKelurahan(p.getDesaKelurahan())
                                .kabupatenKota(p.getKabupatenKota());

                        if (p.getKodepos() != null) {
                            pBuilder.kecamatan(p.getKodepos().getKecamatan())
                                   .desaKelurahan(p.getKodepos().getDesaKelurahan())
                                   .kabupatenKota(p.getKodepos().getKabupatenKota())
                                   .kodePos(p.getKodepos().getKodePos());
                        }

                        if (p.getPersonelId() != null) {
                            User u = personelMap.get(p.getPersonelId());
                            if (u != null) {
                                pBuilder.personelName(u.getNama())
                                       .personelRole(u.getRole() != null ? u.getRole().name() : "N/A");
                            }
                        }

                        return pBuilder.build();
                    }).collect(Collectors.toList());

            MemoDetailResponse.MemoDetailResponseBuilder builder = MemoDetailResponse.builder()
                    .id(memo.getId())
                    .nomorMemo(memo.getNomorMemo())
                    .tanggalMemo(memo.getTanggalMemo())
                    .customerId(memo.getCustomer() != null ? memo.getCustomer().getId() : null)
                    .pelangganMybizId(memo.getPelangganMybiz() != null ? memo.getPelangganMybiz().getId() : null)
                    .customerName(memo.getPelangganMybiz() != null ? memo.getPelangganMybiz().getNamaPartner() : 
                                 (memo.getCustomer() != null ? memo.getCustomer().getNamaPelanggan() : "Tanpa Nama"))
                    .customerPhone(memo.getPelangganMybiz() != null ? memo.getPelangganMybiz().getNoTelepon() : 
                                  (memo.getCustomer() != null ? memo.getCustomer().getNoHp() : "-"))
                    .marketingId(memo.getMarketing() != null ? memo.getMarketing().getId() : null)
                    .marketingName(memo.getMarketingName() != null ? memo.getMarketingName() : (memo.getMarketing() != null ? memo.getMarketing().getNama() : "System"))
                    .marketingEmpCode(memo.getMarketingEmpCode())
                    .marketingUsername(memo.getMarketing() != null ? memo.getMarketing().getUsername() : null)
                    .creatorName(memo.getCreator() != null ? memo.getCreator().getNama() : "System")
                    .statusAkhir(memo.getStatusAkhir())
                    .totalHarga(memo.getTotalHarga() != null ? memo.getTotalHarga() : java.math.BigDecimal.ZERO)
                    .deskripsi(memo.getDeskripsi())
                    .nomorJl(memo.getNomorJl())
                    .memoType(memo.getMemoType())
                    .jenisPrinter(memo.getJenisPrinter())
                    .orderIdMarketplace(memo.getOrderIdMarketplace())
                    .resi(memo.getResi())
                    .ekspedisi(memo.getEkspedisi())
                    .subEkspedisi(memo.getSubEkspedisi())
                    .platform(memo.getPlatform())
                    .tempo(memo.getTempo())
                    .badanUsaha(memo.getBadanUsaha())
                    .revisedFromId(memo.getRevisedFromId())
                    .revisionToId(memo.getRevisionToId())
                    .revisedFromNomorMemo(memo.getRevisedFromId() != null ? 
                        revisedFromMap.get(memo.getRevisedFromId()) : null)
                    .isTeknisRequired(java.lang.Boolean.TRUE.equals(memo.getIsTeknisRequired()))
                    .isDeliveryRequired(java.lang.Boolean.TRUE.equals(memo.getIsDeliveryRequired()))
                    .opsiPengiriman(memo.getOpsiPengiriman())
                    .tipeOngkir(memo.getTipeOngkir())
                    .estimasiOngkir(memo.getEstimasiOngkir())
                    .metodePembayaran(memo.getMetodePembayaran())
                    .buktiFoto(memo.getBuktiFoto())
                    .buktiFotoUrl(memo.getBuktiFoto() != null ? "/uploads/memos/" + memo.getBuktiFoto() : null)
                    .kodePos(memo.getKodePos() != null ? memo.getKodePos() : 
                        (penjadwalanResponses.stream()
                            .filter(p -> p.getKodePos() != null)
                            .map(PenjadwalanResponse::getKodePos)
                            .findFirst()
                            .orElse(null)))
                    .items(itemResponses)
                    .logs(java.util.Collections.emptyList())
                    .penjadwalanHistory(penjadwalanResponses);

            if (!penjadwalanResponses.isEmpty()) {
                PenjadwalanResponse lastJadwal = penjadwalanResponses.get(penjadwalanResponses.size() - 1);
                builder.desaKelurahan(lastJadwal.getDesaKelurahan())
                       .kecamatan(lastJadwal.getKecamatan())
                       .kabupatenKota(lastJadwal.getKabupatenKota());
            } else if (memo.getKodePos() != null && !memo.getKodePos().isBlank()) {
                com.stok.anandam.store.core.postgres.model.Kodepos kp = kodeposMap.get(memo.getKodePos());
                if (kp != null) {
                    builder.desaKelurahan(kp.getDesaKelurahan())
                           .kecamatan(kp.getKecamatan())
                           .kabupatenKota(kp.getKabupatenKota());
                }
            }

            responses.add(builder.build());
        }

        return responses;
    }

    private MemoDetailResponse mapToDetailResponse(Memo memo) {
        List<MemoItemResponse> itemResponses = memoItemRepository.findByMemoId(memo.getId())
                .stream()
                .map(item -> MemoItemResponse.builder()
                        .id(item.getId())
                        .namaBarang(item.getNamaBarang())
                        .qty(item.getQty() != null ? item.getQty() : 0)
                        .qtyShipped(item.getQtyShipped() != null ? item.getQtyShipped() : 0)
                        .qtyRemaining((item.getQty() != null ? item.getQty() : 0) - (item.getQtyShipped() != null ? item.getQtyShipped() : 0))
                        .hargaSatuan(item.getHargaSatuan())
                        .subtotal(item.getSubtotal())
                        .catatanGudang(item.getCatatanGudang())
                        .itemStatus(item.getItemStatus())
                        .status(item.getStatus())
                        .build()
                ).collect(Collectors.toList());

        List<MemoLogResponse> logResponses = memoLogRepository.findByMemoIdOrderByCreatedAtDesc(memo.getId())
                .stream()
                .map(log -> MemoLogResponse.builder()
                        .status(log.getStatus())
                        .aktorId(log.getAktorId())
                        .keterangan(log.getKeterangan())
                        .createdAt(log.getCreatedAt())
                        .build()
                ).collect(Collectors.toList());
        
        List<PenjadwalanResponse> penjadwalanResponses = penjadwalanRepo.findByMemo_IdAndDeletedAtIsNull(memo.getId())
                .stream()
                .map(this::mapToPenjadwalanResponse)
                .collect(Collectors.toList());

        MemoDetailResponse.MemoDetailResponseBuilder builder = MemoDetailResponse.builder()
                .id(memo.getId())
                .nomorMemo(memo.getNomorMemo())
                .tanggalMemo(memo.getTanggalMemo())
                .customerId(memo.getCustomer() != null ? memo.getCustomer().getId() : null)
                .pelangganMybizId(memo.getPelangganMybiz() != null ? memo.getPelangganMybiz().getId() : null)
                .customerName(memo.getPelangganMybiz() != null ? memo.getPelangganMybiz().getNamaPartner() : 
                             (memo.getCustomer() != null ? memo.getCustomer().getNamaPelanggan() : "Tanpa Nama"))
                .customerPhone(memo.getPelangganMybiz() != null ? memo.getPelangganMybiz().getNoTelepon() : 
                              (memo.getCustomer() != null ? memo.getCustomer().getNoHp() : "-"))
                .marketingId(memo.getMarketing() != null ? memo.getMarketing().getId() : null)
                .marketingName(memo.getMarketingName() != null ? memo.getMarketingName() : (memo.getMarketing() != null ? memo.getMarketing().getNama() : "System"))
                .marketingEmpCode(memo.getMarketingEmpCode())
                .marketingUsername(memo.getMarketing() != null ? memo.getMarketing().getUsername() : null)
                .creatorName(memo.getCreator() != null ? memo.getCreator().getNama() : "System")
                .statusAkhir(memo.getStatusAkhir())
                .totalHarga(memo.getTotalHarga() != null ? memo.getTotalHarga() : java.math.BigDecimal.ZERO)
                .deskripsi(memo.getDeskripsi())
                .nomorJl(memo.getNomorJl())
                .memoType(memo.getMemoType())
                .jenisPrinter(memo.getJenisPrinter())
                .orderIdMarketplace(memo.getOrderIdMarketplace())
                .resi(memo.getResi())
                .ekspedisi(memo.getEkspedisi())
                .subEkspedisi(memo.getSubEkspedisi())
                .platform(memo.getPlatform())
                .tempo(memo.getTempo())
                .badanUsaha(memo.getBadanUsaha())
                .revisedFromId(memo.getRevisedFromId())
                .revisionToId(memo.getRevisionToId())
                .revisedFromNomorMemo(memo.getRevisedFromId() != null ? 
                    memoRepository.findById(memo.getRevisedFromId())
                        .map(Memo::getNomorMemo)
                        .orElse(null) : null)
                .isTeknisRequired(java.lang.Boolean.TRUE.equals(memo.getIsTeknisRequired()))
                .isDeliveryRequired(java.lang.Boolean.TRUE.equals(memo.getIsDeliveryRequired()))
                .opsiPengiriman(memo.getOpsiPengiriman())
                .tipeOngkir(memo.getTipeOngkir())
                .estimasiOngkir(memo.getEstimasiOngkir())
                .metodePembayaran(memo.getMetodePembayaran())
                .buktiFoto(memo.getBuktiFoto())
                .buktiFotoUrl(memo.getBuktiFoto() != null ? "/uploads/memos/" + memo.getBuktiFoto() : null)
                .kodePos(memo.getKodePos() != null ? memo.getKodePos() : 
                    (penjadwalanResponses.stream()
                        .filter(p -> p.getKodePos() != null)
                        .map(PenjadwalanResponse::getKodePos)
                        .findFirst()
                        .orElse(null)))
                .items(itemResponses)
                .logs(logResponses)
                .penjadwalanHistory(penjadwalanResponses);

        if (!penjadwalanResponses.isEmpty()) {
            PenjadwalanResponse lastJadwal = penjadwalanResponses.get(penjadwalanResponses.size() - 1);
            builder.desaKelurahan(lastJadwal.getDesaKelurahan())
                   .kecamatan(lastJadwal.getKecamatan())
                   .kabupatenKota(lastJadwal.getKabupatenKota());
        } else if (memo.getKodePos() != null && !memo.getKodePos().isBlank()) {
            kodeposRepository.findFirstByKodePos(memo.getKodePos()).ifPresent(kp -> {
                builder.desaKelurahan(kp.getDesaKelurahan())
                       .kecamatan(kp.getKecamatan())
                       .kabupatenKota(kp.getKabupatenKota());
            });
        }

        return builder.build();
    }

    private PenjadwalanResponse mapToPenjadwalanResponse(com.stok.anandam.store.core.postgres.model.PenjadwalanKonfirmasi p) {
        PenjadwalanResponse.PenjadwalanResponseBuilder builder = PenjadwalanResponse.builder()
                .id(p.getId())
                .memoId(p.getMemo().getId())
                .nomorMemo(p.getMemo().getNomorMemo())
                .tipeTugas(p.getTipeTugas())
                .statusJadwal(p.getStatusJadwal())
                .alamatLengkap(p.getAlamatLengkap())
                .alamatMaps(p.getAlamatMaps())
                .idKodepos(p.getIdKodepos())
                .estimasiWaktu(p.getEstimasiWaktu())
                .catatan(p.getCatatan())
                .tanggalJadwal(p.getTanggalJadwal() != null ? p.getTanggalJadwal().format(DATE_FORMATTER) : null)
                .personelId(p.getPersonelId())
                .latitude(p.getLatitude())
                .longitude(p.getLongitude())
                .kecamatan(p.getKecamatan())
                .desaKelurahan(p.getDesaKelurahan())
                .kabupatenKota(p.getKabupatenKota());
        
        if (p.getKodepos() != null) {
            builder.kecamatan(p.getKodepos().getKecamatan())
                   .desaKelurahan(p.getKodepos().getDesaKelurahan())
                   .kabupatenKota(p.getKodepos().getKabupatenKota())
                   .kodePos(p.getKodepos().getKodePos());
        }

        if (p.getPersonelId() != null) {
            userRepository.findById(p.getPersonelId()).ifPresent(u -> {
                builder.personelName(u.getNama())
                       .personelRole(u.getRole() != null ? u.getRole().name() : "N/A");
            });
        }

        return builder.build();
    }

    private void validateEkspedisi(CreateMemoRequest request) {
        if ("ONLINE".equalsIgnoreCase(request.getMemoType()) && "REGULER".equalsIgnoreCase(request.getEkspedisi())) {
            if (request.getSubEkspedisi() == null || request.getSubEkspedisi().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sub Ekspedisi (JNE, JNT, dsb) wajib diisi jika memilih REGULER");
            }
        }
    }

    private void validateOnlineMemoFields(CreateMemoRequest request) {
        if (!"ONLINE".equalsIgnoreCase(request.getMemoType())) return;

        if (request.getNamaCustomer() == null || request.getNamaCustomer().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gagal: Nama pembeli (customer) wajib diisi untuk memo online");
        }
        if (request.getOrderIdMarketplace() == null || request.getOrderIdMarketplace().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gagal: Order ID Marketplace wajib diisi untuk memo online");
        }
        if (request.getPlatform() == null || request.getPlatform().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gagal: Platform wajib diisi untuk memo online");
        }
        if (request.getEkspedisi() == null || request.getEkspedisi().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gagal: Ekspedisi wajib diisi untuk memo online");
        }
        
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gagal: Item barang tidak boleh kosong");
        }
        
        for (MemoItemRequest item : request.getItems()) {
            if (item.getQty() == null || item.getQty() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gagal: Jumlah barang (Qty) untuk " + item.getNamaBarang() + " tidak boleh 0 atau kosong");
            }
        }
    }

    private void validateOrderId(String orderId, UUID excludeId) {
        if (orderId == null || orderId.isBlank()) return;

        // 1. Cek spasi
        if (orderId.contains(" ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order ID Marketplace tidak boleh mengandung spasi untuk keamanan data");
        }

        // 2. Cek duplikat (Kecuali memo yang statusnya DIBATALKAN)
        boolean exists;
        if (excludeId == null) {
            exists = memoRepository.existsByOrderIdMarketplaceAndStatusAkhirNot(orderId, MemoStatus.DIBATALKAN);
        } else {
            exists = memoRepository.existsByOrderIdMarketplaceAndStatusAkhirNotAndIdNot(orderId, MemoStatus.DIBATALKAN, excludeId);
        }

        if (exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Gagal: Order ID Marketplace " + orderId + " sudah digunakan di memo lain yang masih aktif/selesai.");
        }
    }

    @Transactional
    public WebResponse<String> createMemo(CreateMemoRequest request, String username) {
        validateOnlineMemoFields(request);
        validateEkspedisi(request);

        validateOrderId(request.getOrderIdMarketplace(), null);

        User creator = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        Customer customer = null;
        PelangganMybiz pelangganMybiz = null;

        if (request.getPelangganMybizId() != null) {
            pelangganMybiz = pelangganMybizRepository.findById(request.getPelangganMybizId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pelanggan MyBiz tidak ditemukan"));
        } else if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer tidak ditemukan"));
        } else {
            if (request.getNoHpCustomer() != null && !request.getNoHpCustomer().isBlank()) {
                List<Customer> existingCustomers = customerRepository.findByNoHpAndDeletedAtIsNull(request.getNoHpCustomer());
                String requestedName = request.getNamaCustomer() != null && !request.getNamaCustomer().isBlank() ? request.getNamaCustomer() : "Pelanggan Baru";
                
                customer = existingCustomers.stream()
                        .filter(c -> c.getNamaPelanggan().equalsIgnoreCase(requestedName))
                        .findFirst()
                        .orElseGet(() -> {
                            Customer newCust = Customer.builder()
                                    .namaPelanggan(requestedName)
                                    .noHp(request.getNoHpCustomer())
                                    .build();
                            return customerRepository.save(newCust);
                        });
            } else {
                customer = Customer.builder()
                        .namaPelanggan(request.getNamaCustomer() != null && !request.getNamaCustomer().isBlank() ? request.getNamaCustomer() : "Pelanggan Baru")
                        .build();
                customer = customerRepository.save(customer);
            }
        }

        String nomorMemo = "MEMO-" + request.getMemoType().toUpperCase() + "-" + System.currentTimeMillis();

        Memo memo = Memo.builder()
                .nomorMemo(nomorMemo)
                .customer(customer)
                .pelangganMybiz(pelangganMybiz)
                .marketingName(pelangganMybiz != null ? pelangganMybiz.getNamaMarketing() : request.getNamaMarketing())
                .marketingEmpCode(pelangganMybiz != null ? pelangganMybiz.getKodeMarketing() : request.getMarketingEmpCode())
                .creator(creator)
                .tanggalMemo(request.getTanggal() != null ? LocalDate.parse(request.getTanggal(), DATE_FORMATTER).atStartOfDay() : LocalDateTime.now())
                .isTeknisRequired(request.getIsTeknisi())
                .isDeliveryRequired(request.getMemoType().equalsIgnoreCase("ONLINE") || request.getIsKirim())
                .opsiPengiriman(request.getOpsiPengiriman())
                .tipeOngkir(request.getTipeOngkir())
                .estimasiOngkir(request.getEstimasiOngkir())
                .metodePembayaran(request.getMetodePembayaran())
                .statusAkhir(MemoStatus.DRAFT)
                .totalHarga(request.getTotalHarga())
                .deskripsi(request.getDeskripsi())
                .memoType(request.getMemoType())
                .orderIdMarketplace(request.getOrderIdMarketplace())
                .resi(request.getResi())
                .ekspedisi(request.getEkspedisi())
                .subEkspedisi(request.getSubEkspedisi())
                .platform(request.getPlatform())
                .kodePos(request.getKodePos())
                .tempo(request.getTempo())
                .badanUsaha(request.getBadanUsaha())
                .jenisPrinter(request.getJenisPrinter())
                .revisedFromId(request.getRevisedFromId())
                .build();

        memo = memoRepository.save(memo);

        if (request.getItems() != null) {
            for (MemoItemRequest itemReq : request.getItems()) {
                MemoItem item = MemoItem.builder()
                        .memo(memo)
                        .namaBarang(itemReq.getNamaBarang())
                        .qty(itemReq.getQty())
                        .hargaSatuan(itemReq.getHargaSatuan())
                        .subtotal(itemReq.getSubtotal())
                        .status("NORMAL")
                        .catatanGudang(itemReq.getCatatan())
                        .qtyShipped(0)
                        .build();
                memoItemRepository.save(item);
            }
        }

        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.DRAFT.name(), creator.getId(), "Memo Draft baru dibuat oleh " + username));

        if (request.getRevisedFromId() != null) {
            checkAndApplyRevisionCancellation(memo, creator);
        }

        return WebResponse.<String>builder()
                .data(memo.getId().toString())
                .status(HttpStatus.CREATED.value())
                .message("Memo berhasil dibuat dengan nomor " + nomorMemo)
                .build();
    }

    @Transactional
    public WebResponse<String> updateMemo(UUID memoId, CreateMemoRequest request, String username) {
        validateOnlineMemoFields(request);
        validateEkspedisi(request);
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        validateOrderId(request.getOrderIdMarketplace(), memoId);

        if (memo.getStatusAkhir() != MemoStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hanya memo status DRAFT yang dapat diubah");
        }

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        // Permission check: creator or same role
        boolean isCreator = memo.getCreator() != null && memo.getCreator().getId().equals(aktor.getId());
        boolean isSameRole = memo.getCreator() != null && memo.getCreator().getRole() == aktor.getRole();
        
        if (!isCreator && !isSameRole && !aktor.getRole().name().equals("ADMIN") && !aktor.getRole().name().startsWith("SPV_") && !aktor.getRole().name().equals("MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki hak untuk mengubah memo ini");
        }

        Customer customer = null;
        PelangganMybiz pelangganMybiz = null;

        if (request.getPelangganMybizId() != null) {
            pelangganMybiz = pelangganMybizRepository.findById(request.getPelangganMybizId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pelanggan MyBiz tidak ditemukan"));
        } else if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer tidak ditemukan"));
        } else {
            if (request.getNoHpCustomer() != null && !request.getNoHpCustomer().isBlank()) {
                List<Customer> existingCustomers = customerRepository.findByNoHpAndDeletedAtIsNull(request.getNoHpCustomer());
                String requestedName = request.getNamaCustomer() != null && !request.getNamaCustomer().isBlank() ? request.getNamaCustomer() : "Pelanggan Baru";
                
                customer = existingCustomers.stream()
                        .filter(c -> c.getNamaPelanggan().equalsIgnoreCase(requestedName))
                        .findFirst()
                        .orElseGet(() -> {
                            Customer newCust = Customer.builder()
                                    .namaPelanggan(requestedName)
                                    .noHp(request.getNoHpCustomer())
                                    .build();
                            return customerRepository.save(newCust);
                        });
            } else {
                customer = Customer.builder()
                        .namaPelanggan(request.getNamaCustomer() != null && !request.getNamaCustomer().isBlank() ? request.getNamaCustomer() : "Pelanggan Baru")
                        .build();
                customer = customerRepository.save(customer);
            }
        }

        memo.setCustomer(customer);
        memo.setPelangganMybiz(pelangganMybiz);
        memo.setMarketingName(pelangganMybiz != null ? pelangganMybiz.getNamaMarketing() : request.getNamaMarketing());
        memo.setMarketingEmpCode(pelangganMybiz != null ? pelangganMybiz.getKodeMarketing() : request.getMarketingEmpCode());
        memo.setTanggalMemo(request.getTanggal() != null ? LocalDate.parse(request.getTanggal(), DATE_FORMATTER).atStartOfDay() : LocalDateTime.now());
        memo.setIsTeknisRequired(request.getIsTeknisi());
        memo.setIsDeliveryRequired(request.getMemoType().equalsIgnoreCase("ONLINE") || request.getIsKirim());
        memo.setOpsiPengiriman(request.getOpsiPengiriman());
        memo.setTipeOngkir(request.getTipeOngkir());
        memo.setEstimasiOngkir(request.getEstimasiOngkir());
        memo.setMetodePembayaran(request.getMetodePembayaran());
        memo.setTotalHarga(request.getTotalHarga());
        memo.setDeskripsi(request.getDeskripsi());
        memo.setMemoType(request.getMemoType());
        memo.setOrderIdMarketplace(request.getOrderIdMarketplace());
        memo.setResi(request.getResi());
        memo.setEkspedisi(request.getEkspedisi());
        memo.setSubEkspedisi(request.getSubEkspedisi());
        memo.setPlatform(request.getPlatform());
        memo.setKodePos(request.getKodePos());
        memo.setTempo(request.getTempo());
        memo.setBadanUsaha(request.getBadanUsaha());
        memo.setJenisPrinter(request.getJenisPrinter());

        memoRepository.save(memo);

        memoItemRepository.deleteByMemoId(memoId);

        if (request.getItems() != null) {
            for (MemoItemRequest itemReq : request.getItems()) {
                MemoItem item = MemoItem.builder()
                        .memo(memo)
                        .namaBarang(itemReq.getNamaBarang())
                        .qty(itemReq.getQty())
                        .hargaSatuan(itemReq.getHargaSatuan())
                        .subtotal(itemReq.getSubtotal())
                        .status("NORMAL")
                        .catatanGudang(itemReq.getCatatan())
                        .qtyShipped(0)
                        .build();
                memoItemRepository.save(item);
            }
        }

        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.DRAFT.name(), aktor.getId(), "Memo Draft diperbarui oleh " + username));

        checkAndApplyRevisionCancellation(memo, aktor);

        sendMemoRefreshSignal();

        return WebResponse.<String>builder()
                .data(memo.getId().toString())
                .status(HttpStatus.OK.value())
                .message("Memo berhasil diperbarui")
                .build();
    }

    @Transactional
    public WebResponse<String> updateResi(UUID memoId, UpdateResiRequest request, String username) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        String oldResi = memo.getResi() != null ? memo.getResi() : "-";
        memo.setResi(request.getResi());
        memoRepository.save(memo);

        memoLogRepository.save(new MemoLog(memo.getId(), memo.getStatusAkhir().name(), aktor.getId(), 
                "Nomor Resi diperbarui dari " + oldResi + " menjadi " + request.getResi() + " oleh " + username));

        sendMemoRefreshSignal();

        return WebResponse.<String>builder()
                .data("OK")
                .status(200)
                .message("Nomor Resi berhasil diperbarui")
                .build();
    }

    @Transactional
    public WebResponse<String> updateMemoStatus(UUID memoId, UpdateMemoStatusRequest request, String username) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        MemoStatus targetStatus = request.getTargetStatus();
        memo.setStatusAkhir(targetStatus);

        // Jika dibatalkan, hapus (soft-delete) juga semua jadwal yang terkait
        if (targetStatus == MemoStatus.DIBATALKAN) {
            List<PenjadwalanKonfirmasi> relatedJadwal = penjadwalanRepo.findByMemo_IdAndDeletedAtIsNull(memoId);
            for (PenjadwalanKonfirmasi jadwal : relatedJadwal) {
                if (jadwal.getStatusJadwal() != StatusJadwal.SELESAI) {
                    jadwal.setStatusJadwal(StatusJadwal.DIBATALKAN);
                    penjadwalanRepo.save(jadwal);
                }
            }
        }

        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), targetStatus.name(), aktor.getId(), request.getKeteranganLog()));

        // Audit Log
        activityLogService.log(username, "MEMO_STATUS_UPDATE", 
            "Mengubah status Memo " + memo.getNomorMemo() + " menjadi " + targetStatus);

        sendMemoRefreshSignal();

        return WebResponse.<String>builder()
                .data("OK")
                .status(200)
                .message("Status memo berhasil diubah menjadi " + targetStatus)
                .build();
    }

    @Transactional
    public WebResponse<String> addPenjadwalan(UUID memoId, CreatePenjadwalanRequest request, String username) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        TipeTugas tipe = TipeTugas.valueOf(request.getTipeTugas().toUpperCase());

        PenjadwalanKonfirmasi jadwal = penjadwalanRepo.findByMemo_IdAndDeletedAtIsNull(memoId).stream()
                .filter(j -> j.getTipeTugas() == tipe)
                .findFirst()
                .orElse(new PenjadwalanKonfirmasi());

        jadwal.setMemo(memo);
        jadwal.setTipeTugas(tipe);
        jadwal.setTanggalJadwal(request.getTanggalJadwal() != null ? LocalDate.parse(request.getTanggalJadwal(), DATE_FORMATTER) : LocalDate.now());
        jadwal.setAlamatLengkap(request.getAlamatLengkap());
        jadwal.setAlamatMaps(request.getAlamatMaps());
        jadwal.setLatitude(request.getLatitude());
        jadwal.setLongitude(request.getLongitude());
        jadwal.setIdKodepos(request.getIdKodepos());
        jadwal.setEstimasiWaktu(request.getEstimasiWaktu());
        jadwal.setCatatan(request.getCatatan());
        
        // Save regional data components
        jadwal.setKabupatenKota(request.getKabupatenKota());
        jadwal.setKecamatan(request.getKecamatan());
        jadwal.setDesaKelurahan(request.getDesaKelurahan());
        
        if (jadwal.getId() == null) {
            jadwal.setStatusJadwal(StatusJadwal.MENUNGGU_KONFIRMASI);
        }

        if (aktor.getRole().name().equals("GUDANG") || aktor.getRole().name().equals("ADMIN") || aktor.getRole().name().equals("MANAGER")) {
            jadwal.setStatusJadwal(StatusJadwal.DIJADWALKAN);
        }

        penjadwalanRepo.save(jadwal);

        boolean isChanged = false;
        if (tipe == TipeTugas.PENGIRIMAN && !Boolean.TRUE.equals(memo.getIsDeliveryRequired())) {
            memo.setIsDeliveryRequired(true);
            memo.setOpsiPengiriman("DELIVERY");
            isChanged = true;
        } else if (tipe == TipeTugas.TEKNISI && !Boolean.TRUE.equals(memo.getIsTeknisRequired())) {
            memo.setIsTeknisRequired(true);
            isChanged = true;
        }

        if (isChanged) {
            memoRepository.save(memo);
        }

        String logMessage = "Permintaan penjadwalan " + tipe + " ditambahkan";
        if (jadwal.getStatusJadwal() == StatusJadwal.DIJADWALKAN) {
            logMessage = "Jadwal " + tipe + " telah dikonfirmasi oleh " + username;
        }
        memoLogRepository.save(new MemoLog(memo.getId(), memo.getStatusAkhir().name(), aktor.getId(), logMessage));

        sendMemoRefreshSignal();

        return WebResponse.<String>builder()
                .data("OK")
                .status(HttpStatus.CREATED.value())
                .message("Penjadwalan " + tipe + " berhasil disimpan")
                .build();
    }

    @Transactional
    public WebResponse<String> konfirmasiKirim(UUID memoId, String itemsJson, MultipartFile photo, String username) {
        KonfirmasiKirimRequest request;
        try {
            request = objectMapper.readValue(itemsJson, KonfirmasiKirimRequest.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format data item tidak valid");
        }

        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data item kirim tidak boleh kosong");
        }

        for (ItemKirim itemInput : request.getItems()) {
            MemoItem item = memoItemRepository.findById(itemInput.getItemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item dengan ID " + itemInput.getItemId() + " tidak ditemukan"));

            int qtyBaru = item.getQtyShipped() + itemInput.getQtyDikirimSaatIni();
            if (qtyBaru > item.getQty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kuantitas kirim melebihi batas pesanan untuk item: " + item.getNamaBarang());
            }

            item.setQtyShipped(qtyBaru);
            memoItemRepository.save(item);
        }

        List<MemoItem> allItems = memoItemRepository.findByMemoId(memoId);
        boolean isSemuaTerkirim = allItems.stream().allMatch(i -> i.getQtyShipped() >= i.getQty());
        MemoStatus targetStatus = isSemuaTerkirim ? MemoStatus.SELESAI : MemoStatus.TERKIRIM_SEBAGIAN;   
        memo.setStatusAkhir(targetStatus);

        if (photo != null && !photo.isEmpty()) {
            validateImageFile(photo);
            try {
                String fileName = fileService.saveMemoPhoto(photo);
                memo.setBuktiFoto(fileName);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal menyimpan foto bukti");
            }
        }

        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), targetStatus.name(), aktor.getId(), "Konfirmasi pengiriman oleh " + username));
        
        // Audit Log
        activityLogService.log(username, "MEMO_DELIVERY_CONFIRMED", 
            "Konfirmasi pengiriman Memo " + memo.getNomorMemo() + ". Status akhir: " + targetStatus);
        
        syncTaskStatus(memo.getId(), StatusJadwal.SELESAI, null);

        sendMemoRefreshSignal();

        return WebResponse.<String>builder()
                .status(HttpStatus.OK.value())
                .message("Konfirmasi pengiriman berhasil disimpan")
                .data(memo.getId().toString())
                .build();
    }

    @Transactional
    public WebResponse<String> updateItemCatatan(Long itemId, UpdateItemCatatanRequest request, String username) {
        MemoItem item = memoItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item tidak ditemukan"));

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        if (!aktor.getRole().name().equals("GUDANG") && !aktor.getRole().name().equals("SPV_GUDANG") && !aktor.getRole().name().equals("ADMIN") && !aktor.getRole().name().equals("MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Akses ditolak: Hanya bagian gudang yang bisa mengisi catatan item");
        }

        item.setCatatanGudang(request.getCatatanGudang());
        memoItemRepository.save(item);

        return WebResponse.<String>builder()
                .data("OK")
                .status(200)
                .message("Catatan gudang untuk item berhasil diperbarui")
                .build();
    }

    @Transactional
    public WebResponse<String> updateItemStatus(UUID memoId, Long itemId, UpdateItemStatusRequest request, String username) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        MemoItem item = memoItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item tidak ditemukan"));

        if (!item.getMemo().getId().equals(memo.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item tidak terdaftar di memo ini");
        }

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        item.setItemStatus(request.getItemStatus());
        memoItemRepository.save(item);

        // Audit Log
        activityLogService.log(username, "ITEM_STATUS_UPDATE",
                "Mengubah status item " + item.getNamaBarang() + " menjadi " + request.getItemStatus() + " pada memo " + memo.getNomorMemo());

        sendMemoRefreshSignal();

        return WebResponse.<String>builder()
                .data("OK")
                .status(200)
                .message("Status item berhasil diperbarui menjadi " + request.getItemStatus())
                .build();
    }

    @Transactional
    public WebResponse<String> createPendingMemo(CreateMemoRequest request, String username) {
        validateOnlineMemoFields(request);
        validateOrderId(request.getOrderIdMarketplace(), null);

        User creator = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        Customer customer = null;
        PelangganMybiz pelangganMybiz = null;

        if (request.getPelangganMybizId() != null) {
            pelangganMybiz = pelangganMybizRepository.findById(request.getPelangganMybizId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pelanggan MyBiz tidak ditemukan"));
        } else if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer tidak ditemukan"));
        } else {
            if (request.getNoHpCustomer() != null && !request.getNoHpCustomer().isBlank()) {
                List<Customer> existingCustomers = customerRepository.findByNoHpAndDeletedAtIsNull(request.getNoHpCustomer());
                String requestedName = request.getNamaCustomer() != null && !request.getNamaCustomer().isBlank() ? request.getNamaCustomer() : "Pelanggan Baru";
                
                customer = existingCustomers.stream()
                        .filter(c -> c.getNamaPelanggan().equalsIgnoreCase(requestedName))
                        .findFirst()
                        .orElseGet(() -> {
                            Customer newCust = Customer.builder()
                                    .namaPelanggan(requestedName)
                                    .noHp(request.getNoHpCustomer())
                                    .build();
                            return customerRepository.save(newCust);
                        });
            } else {
                customer = Customer.builder()
                        .namaPelanggan(request.getNamaCustomer() != null && !request.getNamaCustomer().isBlank() ? request.getNamaCustomer() : "Pelanggan Baru")
                        .build();
                customer = customerRepository.save(customer);
            }
        }

        String nomorMemo = "MEMO-PENDING-" + request.getMemoType().toUpperCase() + "-" + System.currentTimeMillis();

        Memo memo = Memo.builder()
                .nomorMemo(nomorMemo)
                .customer(customer)
                .pelangganMybiz(pelangganMybiz)
                .marketingName(pelangganMybiz != null ? pelangganMybiz.getNamaMarketing() : request.getNamaMarketing())
                .marketingEmpCode(pelangganMybiz != null ? pelangganMybiz.getKodeMarketing() : request.getMarketingEmpCode())
                .creator(creator)
                .tanggalMemo(request.getTanggal() != null ? LocalDate.parse(request.getTanggal(), DATE_FORMATTER).atStartOfDay() : LocalDateTime.now())
                .isTeknisRequired(request.getIsTeknisi())
                .isDeliveryRequired(request.getMemoType().equalsIgnoreCase("ONLINE") || request.getIsKirim())
                .opsiPengiriman(request.getOpsiPengiriman())
                .tipeOngkir(request.getTipeOngkir())
                .estimasiOngkir(request.getEstimasiOngkir())
                .metodePembayaran(request.getMetodePembayaran())
                .statusAkhir(MemoStatus.MENUNGGU_PERSETUJUAN)
                .totalHarga(request.getTotalHarga())
                .deskripsi(request.getDeskripsi())
                .memoType(request.getMemoType())
                .orderIdMarketplace(request.getOrderIdMarketplace())
                .resi(request.getResi())
                .ekspedisi(request.getEkspedisi())
                .subEkspedisi(request.getSubEkspedisi())
                .platform(request.getPlatform())
                .kodePos(request.getKodePos())
                .tempo(request.getTempo())
                .badanUsaha(request.getBadanUsaha())
                .jenisPrinter(request.getJenisPrinter())
                .build();

        memo = memoRepository.save(memo);

        if (request.getItems() != null) {
            for (MemoItemRequest itemReq : request.getItems()) {
                MemoItem item = MemoItem.builder()
                        .memo(memo)
                        .namaBarang(itemReq.getNamaBarang())
                        .qty(itemReq.getQty())
                        .hargaSatuan(itemReq.getHargaSatuan())
                        .subtotal(itemReq.getSubtotal())
                        .status("NORMAL")
                        .qtyShipped(0)
                        .build();
                memoItemRepository.save(item);
            }
        }

        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.MENUNGGU_PERSETUJUAN.name(), creator.getId(), "Memo Pending baru diajukan oleh " + username));

        sendMemoRefreshSignal();

        return WebResponse.<String>builder()
                .data(memo.getId().toString())
                .status(HttpStatus.CREATED.value())
                .message("Memo Pending berhasil diajukan")
                .build();
    }

    @Transactional
    public WebResponse<String> approvePendingMemo(UUID memoId, String username) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        if (!aktor.getRole().name().equals("ADMIN") && 
            !aktor.getRole().name().equals("SPV_MARKETING") && 
            !aktor.getRole().name().equals("SPV_GUDANG") && 
            !aktor.getRole().name().equals("GUDANG") && 
            !aktor.getRole().name().equals("MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya Admin, Supervisor, atau Bagian Gudang yang dapat menyetujui memo");
        }

        memo.setStatusAkhir(MemoStatus.DISETUJUI);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.DISETUJUI.name(), aktor.getId(), "Memo Pending disetujui oleh " + username));

        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Memo berhasil disetujui").build();
    }

    @Transactional
    public WebResponse<String> rejectPendingMemo(UUID memoId, String username, String alasan) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        if (!aktor.getRole().name().equals("ADMIN") && 
            !aktor.getRole().name().equals("SPV_MARKETING") && 
            !aktor.getRole().name().equals("SPV_GUDANG") && 
            !aktor.getRole().name().equals("GUDANG") && 
            !aktor.getRole().name().equals("MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya Admin, Supervisor, atau Bagian Gudang yang dapat menolak memo");
        }

        memo.setStatusAkhir(MemoStatus.DITOLAK);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.DITOLAK.name(), aktor.getId(), "Memo Pending ditolak oleh " + username + ". Alasan: " + alasan));

        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Memo Pending berhasil ditolak").build();
    }

    @Transactional
    public WebResponse<String> releasePendingMemo(UUID id, String username) {
        Memo memo = memoRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        memo.setStatusAkhir(MemoStatus.DRAFT);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.DRAFT.name(), aktor.getId(), "Memo Pending dirilis kembali ke Draft oleh " + username));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Memo berhasil dirilis").build();
    }

    @Transactional
    public WebResponse<String> continueToMemo(UUID id, UpdateMemoTypeRequest request, String username) {
        Memo memo = memoRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        validateOnlineMemoFields(request.getDetails());
        validateOrderId(request.getDetails().getOrderIdMarketplace(), id);

        memo.setMemoType(request.getDetails().getMemoType());
        memo.setOrderIdMarketplace(request.getDetails().getOrderIdMarketplace());
        memo.setResi(request.getDetails().getResi());
        memo.setEkspedisi(request.getDetails().getEkspedisi());
        memo.setSubEkspedisi(request.getDetails().getSubEkspedisi());
        memo.setPlatform(request.getDetails().getPlatform());
        memo.setKodePos(request.getDetails().getKodePos());
        memo.setTempo(request.getDetails().getTempo());
        memo.setBadanUsaha(request.getDetails().getBadanUsaha());
        memo.setJenisPrinter(request.getDetails().getJenisPrinter());

        memo.setIsTeknisRequired(request.getDetails().getIsTeknisi());
        memo.setIsDeliveryRequired(request.getDetails().getMemoType().equalsIgnoreCase("ONLINE") || request.getDetails().getIsKirim());
        memo.setOpsiPengiriman(request.getDetails().getOpsiPengiriman());
        memo.setTipeOngkir(request.getDetails().getTipeOngkir());
        memo.setMetodePembayaran(request.getDetails().getMetodePembayaran());

        memo.setStatusAkhir(MemoStatus.MENUNGGU_GUDANG);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.MENUNGGU_GUDANG.name(), aktor.getId(), "Memo Pending dilanjutkan menjadi Memo " + request.getDetails().getMemoType() + " oleh " + username));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Memo berhasil dilanjutkan").build();
    }

    @Transactional
    public WebResponse<String> finishPendingMemo(UUID id, String username) {
        Memo memo = memoRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        memo.setStatusAkhir(MemoStatus.SELESAI);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.SELESAI.name(), aktor.getId(), "Memo Pending diselesaikan oleh " + username));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Memo berhasil diselesaikan").build();
    }

    @Transactional
    public WebResponse<String> finalizeMemo(UUID memoId, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        memo.setStatusAkhir(MemoStatus.MENUNGGU_GUDANG);
        memoRepository.save(memo);
        
        checkAndApplyRevisionCancellation(memo, aktor);
        
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.MENUNGGU_GUDANG.name(), aktor.getId(), "Memo difinalisasi ke Gudang oleh " + username));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Memo berhasil difinalisasi").build();
    }

    private boolean tryAutoMatchJl(Memo memo) {
        // 1. Ambil nama pelanggan dari Memo
        String customerName = null;
        if (memo.getPelangganMybiz() != null) {
            customerName = memo.getPelangganMybiz().getNamaPartner();
        } else if (memo.getCustomer() != null) {
            customerName = memo.getCustomer().getNamaPelanggan();
        }
        
        if (customerName == null || customerName.isBlank()) return false;
        if (memo.getTotalHarga() == null || memo.getTotalHarga().compareTo(BigDecimal.ZERO) == 0) return false;
        
        // Normalisasi nama: trim dan collapse multiple spaces menjadi 1 spasi
        String normalizedName = customerName.trim().replaceAll("\\s+", " ");
        
        // Rentang minimal tanggal transaksi: H-3 dari tanggal memo / pembuatan memo
        LocalDateTime memoDateTime = memo.getTanggalMemo() != null ? memo.getTanggalMemo() : memo.getCreatedAt();
        if (memoDateTime == null) {
            memoDateTime = LocalDateTime.now();
        }
        LocalDate minDate = memoDateTime.toLocalDate().minusDays(3);

        // 2. Cari di Sales (sudah GROUP BY docNo ORDER BY MAX(docDate) DESC, jadi index 0 = terbaru)
        List<String> matches = salesRepository.findDocNoForAutoMatch(
            normalizedName, memo.getTotalHarga(), minDate);
        
        if (matches.isEmpty()) return false;
        
        // 3. Ambil yang terbaru (index 0)
        String bestMatchDocNo = matches.get(0);
        memo.setNomorJl(bestMatchDocNo);
        return true;
    }

    @Transactional
    public WebResponse<String> retryAutoMatchJl(UUID memoId, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        
        if (memo.getStatusAkhir() != MemoStatus.MENUNGGU_NOTA && memo.getStatusAkhir() != MemoStatus.MENUNGGU_GUDANG) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Retry auto-match JL hanya bisa dilakukan saat status MENUNGGU_GUDANG atau MENUNGGU_NOTA");
        }
        
        boolean matched = tryAutoMatchJl(memo);
        if (!matched) {
            return WebResponse.<String>builder().data("NOT_FOUND").status(200)
                .message("JL tidak ditemukan. Silakan input manual atau tunggu sync data MyBiz.").build();
        }
        
        // Jika JL ditemukan, langsung lanjut ke next status, tidak peduli MENUNGGU_GUDANG atau MENUNGGU_NOTA
        MemoStatus targetStatus = Boolean.TRUE.equals(memo.getIsTeknisRequired())
                ? MemoStatus.MENUNGGU_TEKNISI : MemoStatus.BUFFER_ZONE;
        memo.setStatusAkhir(targetStatus);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), targetStatus.name(), aktor.getId(),
            "JL otomatis ditemukan via retry: " + memo.getNomorJl() + ". Status langsung masuk " + targetStatus.name()));
        
        sendMemoRefreshSignal();
        return WebResponse.<String>builder().data("OK").status(200)
            .message("JL otomatis ditemukan: " + memo.getNomorJl()).build();
    }

    @Transactional
    public WebResponse<String> retryAutoMatchJlBulk(String username) {
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        
        // Cari memo dengan status MENUNGGU_GUDANG atau MENUNGGU_NOTA yang belum punya JL
        List<Memo> waitingMemos = memoRepository.findByStatusAkhirInAndNomorJlIsNullOrEmpty(
                List.of(MemoStatus.MENUNGGU_GUDANG, MemoStatus.MENUNGGU_NOTA)
        );
        if (waitingMemos.isEmpty()) {
            return WebResponse.<String>builder().data("NO_MEMOS").status(200)
                .message("Tidak ada memo yang membutuhkan pencarian JL saat ini.").build();
        }
        
        int matchCount = 0;
        for (Memo memo : waitingMemos) {
            boolean matched = tryAutoMatchJl(memo);
            if (matched) {
                // Jika JL ditemukan, langsung lanjut ke next status
                MemoStatus targetStatus = Boolean.TRUE.equals(memo.getIsTeknisRequired())
                        ? MemoStatus.MENUNGGU_TEKNISI : MemoStatus.BUFFER_ZONE;
                memo.setStatusAkhir(targetStatus);
                memoRepository.save(memo);
                memoLogRepository.save(new MemoLog(memo.getId(), targetStatus.name(), aktor.getId(),
                    "JL otomatis ditemukan via bulk retry: " + memo.getNomorJl() + ". Status langsung masuk " + targetStatus.name()));
                matchCount++;
            }
        }
        
        if (matchCount > 0) {
            sendMemoRefreshSignal();
        }
        
        return WebResponse.<String>builder()
            .data("OK")
            .status(200)
            .message("Berhasil mencocokkan " + matchCount + " dari " + waitingMemos.size() + " memo.")
            .build();
    }

    @Transactional
    public WebResponse<String> finishWarehouseProcess(UUID memoId, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        
        // Set status awal ke MENUNGGU_NOTA
        memo.setStatusAkhir(MemoStatus.MENUNGGU_NOTA);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.MENUNGGU_NOTA.name(), aktor.getId(), "Gudang selesai menyiapkan barang. Menunggu Nota/Invoice."));
        
        // === FITUR BARU: Auto-match JL ===
        boolean jlMatched = tryAutoMatchJl(memo);
        if (jlMatched) {
            MemoStatus targetStatus = Boolean.TRUE.equals(memo.getIsTeknisRequired())
                    ? MemoStatus.MENUNGGU_TEKNISI
                    : MemoStatus.BUFFER_ZONE;
            memo.setStatusAkhir(targetStatus);
            memoLogRepository.save(new MemoLog(memo.getId(), targetStatus.name(), aktor.getId(),
                "JL otomatis ditemukan: " + memo.getNomorJl() + ". Status langsung masuk " + targetStatus.name()));
        }
        
        memoRepository.save(memo);
        sendMemoRefreshSignal();
        
        String message = jlMatched 
            ? "Proses gudang selesai. JL otomatis: " + memo.getNomorJl()
            : "Proses gudang selesai. JL belum ditemukan, menunggu input manual.";
        return WebResponse.<String>builder().data("OK").status(200).message(message).build();
    }

    @Transactional
    public WebResponse<String> finishInvoicingProcess(UUID memoId, FinishNotaRequest request, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        memo.setNomorJl(request.getNomorJl());
        // Langsung masuk BUFFER_ZONE setelah input JL (status DIBUAT_NOTA dihapus)
        MemoStatus targetStatus = java.lang.Boolean.TRUE.equals(memo.getIsTeknisRequired())
                ? MemoStatus.MENUNGGU_TEKNISI
                : MemoStatus.BUFFER_ZONE;
        memo.setStatusAkhir(targetStatus);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), targetStatus.name(), aktor.getId(), "Nomor JL diinput (" + request.getNomorJl() + ") oleh " + username + ". Status langsung masuk " + targetStatus.name()));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Nomor JL berhasil diinput, memo masuk " + targetStatus.name()).build();
    }

    @Transactional
    public WebResponse<String> confirmDeliveryRoute(UUID memoId, FinishGudangRequest request, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        LocalDate tglJadwal = null;
        if (request.getTanggalJadwal() != null && !request.getTanggalJadwal().isBlank()) {
            tglJadwal = LocalDate.parse(request.getTanggalJadwal(), DATE_FORMATTER);
        }

        if (request.getDriverId() != null) {
            PenjadwalanKonfirmasi jadwal = penjadwalanRepo.findByMemo_IdAndDeletedAtIsNull(memoId).stream()
                    .filter(j -> j.getTipeTugas() == TipeTugas.PENGIRIMAN)
                    .findFirst()
                    .orElseGet(() -> {
                        PenjadwalanKonfirmasi newJadwal = new PenjadwalanKonfirmasi();
                        newJadwal.setMemo(memo);
                        newJadwal.setTipeTugas(TipeTugas.PENGIRIMAN);
                        return newJadwal;
                    });
            jadwal.setPersonelId(request.getDriverId());
            jadwal.setStatusJadwal(StatusJadwal.DIJADWALKAN);
            if (tglJadwal != null) jadwal.setTanggalJadwal(tglJadwal);
            penjadwalanRepo.save(jadwal);
        }

        if (request.getTeknisiId() != null) {
            PenjadwalanKonfirmasi jadwal = penjadwalanRepo.findByMemo_IdAndDeletedAtIsNull(memoId).stream()
                    .filter(j -> j.getTipeTugas() == TipeTugas.TEKNISI)
                    .findFirst()
                    .orElseGet(() -> {
                        PenjadwalanKonfirmasi newJadwal = new PenjadwalanKonfirmasi();
                        newJadwal.setMemo(memo);
                        newJadwal.setTipeTugas(TipeTugas.TEKNISI);
                        return newJadwal;
                    });
            jadwal.setPersonelId(request.getTeknisiId());
            jadwal.setStatusJadwal(StatusJadwal.DIJADWALKAN);
            if (tglJadwal != null) jadwal.setTanggalJadwal(tglJadwal);
            penjadwalanRepo.save(jadwal);
        }

        MemoStatus targetStatus = MemoStatus.MENUNGGU_PENGIRIMAN;
        String logMsg = "Jalur pengiriman telah dikonfirmasi oleh " + username;

        if (request.getTeknisiId() != null) {
            targetStatus = MemoStatus.MENUNGGU_TEKNISI;
            logMsg = "Penugasan teknisi telah dikonfirmasi oleh " + username;
        }

        memo.setStatusAkhir(targetStatus);
        memoRepository.save(memo);

        // Generate QR Code untuk keperluan scan delivery
        if (memo.getQrCode() == null || memo.getQrCode().isBlank()) {
            String qrData = "{ \"memoId\": \"" + memo.getId() + "\", \"nomor\": \"" + memo.getNomorMemo() + "\" }";
            memo.setQrCode(qrData);
            memoRepository.save(memo);
        }

        memoLogRepository.save(new MemoLog(memo.getId(), targetStatus.name(), aktor.getId(), logMsg));

        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Penugasan/Rute berhasil dikonfirmasi").build();
    }

    @Transactional
    public WebResponse<String> confirmPickupRoute(UUID memoId, MultipartFile photo, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        if (photo != null && !photo.isEmpty()) {
            validateImageFile(photo);
            String fileName = "memo_pickup_" + memoId + "_" + System.currentTimeMillis() + ".jpg";
            try {
                java.nio.file.Path path = java.nio.file.Paths.get("uploads/memos/" + fileName);
                java.nio.file.Files.createDirectories(path.getParent());
                java.nio.file.Files.write(path, photo.getBytes());
                memo.setBuktiFoto(fileName);
            } catch (java.io.IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal mengunggah foto: " + e.getMessage());
            }
        }

        memo.setStatusAkhir(MemoStatus.DITERIMA_USER);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.DITERIMA_USER.name(), aktor.getId(), "Serah terima barang (Gudang) telah difoto oleh " + username + ". Status berubah menjadi Diterima User."));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Pickup berhasil difoto, status berubah menjadi Diterima User").build();
    }

    @Transactional
    public WebResponse<String> confirmPickupFinal(UUID memoId, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        String role = aktor.getRole().name();
        boolean isMarketing = role.startsWith("MARKETING_") || "MARKETING".equals(role);
        boolean isAdminOrSpv = "ADMIN".equals(role) || "SPV_MARKETING".equals(role) || "MANAGER".equals(role);

        if (!isMarketing && !isAdminOrSpv) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses untuk konfirmasi selesai memo ini.");
        }

        memo.setStatusAkhir(MemoStatus.SELESAI);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.SELESAI.name(), aktor.getId(), "Serah terima barang dikonfirmasi SELESAI oleh " + username));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Memo berhasil diselesaikan").build();
    }

    @Transactional
    public WebResponse<String> reportPhysicalIssue(UUID memoId, UpdateMemoStatusRequest request, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        memo.setStatusAkhir(MemoStatus.KENDALA_BARANG);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.KENDALA_BARANG.name(), aktor.getId(), "Kendala barang dilaporkan oleh " + username));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Kendala berhasil dilaporkan").build();
    }

    @Transactional
    public WebResponse<String> forceComplete(UUID memoId, UpdateMemoStatusRequest request, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        memo.setStatusAkhir(MemoStatus.SELESAI);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.SELESAI.name(), aktor.getId(), "Memo diselesaikan secara paksa oleh Admin: " + username));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Memo berhasil diselesaikan secara paksa").build();
    }

    @Transactional
    public WebResponse<String> finishTechnicianProcess(UUID memoId, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        
        MemoStatus targetStatus = MemoStatus.SELESAI;
        String logKeterangan = "Proses teknisi selesai untuk memo ini";

        if (java.lang.Boolean.TRUE.equals(memo.getIsDeliveryRequired())) {
            targetStatus = MemoStatus.BUFFER_ZONE;
            logKeterangan = "Proses teknisi selesai. Memo masuk ke Buffer Zone untuk pengiriman.";
        }

        // Mark the technician task as SELESAI in penjadwalan
        List<PenjadwalanKonfirmasi> tasks = penjadwalanRepo.findByMemo_IdAndDeletedAtIsNull(memoId);
        for (PenjadwalanKonfirmasi task : tasks) {
            if (task.getTipeTugas() == TipeTugas.TEKNISI && task.getStatusJadwal() != StatusJadwal.SELESAI) {
                task.setStatusJadwal(StatusJadwal.SELESAI);
                penjadwalanRepo.save(task);
            }
        }

        memo.setStatusAkhir(targetStatus);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), targetStatus.name(), aktor.getId(), logKeterangan));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Proses teknisi selesai").build();
    }

    @Transactional
    public WebResponse<String> finishDeliveryProcess(UUID memoId, MultipartFile photo, String catatan, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        String fileName = null;
        if (photo != null && !photo.isEmpty()) {
            validateImageFile(photo);
            fileName = "memo_delivery_" + memoId + "_" + System.currentTimeMillis() + ".jpg";
            try {
                java.nio.file.Path path = java.nio.file.Paths.get("uploads/memos/" + fileName);
                java.nio.file.Files.createDirectories(path.getParent());
                java.nio.file.Files.write(path, photo.getBytes());
            } catch (java.io.IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal mengunggah foto: " + e.getMessage());
            }
        }
        memo.setStatusAkhir(MemoStatus.DITERIMA_USER);
        memo.setBuktiFoto(fileName);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.DITERIMA_USER.name(), aktor.getId(), "Pengiriman diselesaikan oleh User: " + aktor.getNama() + " (Diterima User). Catatan: " + (catatan != null ? catatan : "-")));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Pengiriman selesai, status: Diterima User").build();
    }

    // ─── PHOTO EVIDENCE (khusus upload foto tanpa ubah status) ──────────────────

    @Transactional
    public WebResponse<String> uploadEvidencePhoto(UUID memoId, MultipartFile photo, String username) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        if (photo == null || photo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File foto tidak boleh kosong");
        }

        validateImageFile(photo);
        String fileName = "memo_evidence_" + memoId + "_" + System.currentTimeMillis() + ".jpg";
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("uploads/memos/" + fileName);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, photo.getBytes());
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gagal menyimpan foto: " + e.getMessage());
        }

        // Simpan nama file foto ke memo (overwrite jika sudah ada)
        memo.setBuktiFoto(fileName);
        memoRepository.save(memo);

        // Catat sebagai log audit
        memoLogRepository.save(new MemoLog(
            memo.getId(), 
            memo.getStatusAkhir().name(), 
            aktor.getId(), 
            "Foto bukti (evidence) diupload oleh " + aktor.getNama()
        ));

        // Audit trail
        activityLogService.log(username, "MEMO_EVIDENCE_PHOTO",
            "Foto bukti diupload untuk Memo " + memo.getNomorMemo());

        sendMemoRefreshSignal();

        return WebResponse.<String>builder()
                .data(fileName)
                .status(200)
                .message("Foto bukti berhasil disimpan")
                .build();
    }

    /**
     * Ambil nama file foto bukti dari memo (untuk endpoint GET /api/v1/memos/{id}/photo)
     */
    @Transactional(readOnly = true)
    public String getMemoPhotoFileName(UUID memoId) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        return memo.getBuktiFoto();
    }

    @Transactional
    public WebResponse<String> completeMemo(UUID memoId, String username) {
        Memo memo = memoRepository.findById(memoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));
        User aktor = userRepository.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));
        memo.setStatusAkhir(MemoStatus.SELESAI);
        memoRepository.save(memo);
        memoLogRepository.save(new MemoLog(memo.getId(), MemoStatus.SELESAI.name(), aktor.getId(), "Memo dinyatakan Selesai oleh " + username));
        
        sendMemoRefreshSignal();

        return WebResponse.<String>builder().data("OK").status(200).message("Memo selesai").build();
    }

    @Transactional
    public WebResponse<String> syncTaskStatus(UUID memoId, StatusJadwal status, String notes) {
        List<PenjadwalanKonfirmasi> existingTasks = penjadwalanRepo.findByMemo_IdAndDeletedAtIsNull(memoId);
        for (PenjadwalanKonfirmasi task : existingTasks) {
            task.setStatusJadwal(status);
            if (notes != null) {
                task.setCatatan((task.getCatatan() != null ? task.getCatatan() : "") + " | Sync Note: " + notes);
            }
            penjadwalanRepo.save(task);
        }
        return WebResponse.<String>builder()
                .data("OK")
                .status(200)
                .message("Tasks sync successfully")
                .build();
    }

    @Transactional
    public WebResponse<MemoDetailResponse> duplicateRevision(UUID memoId, String username) {
        Memo oldMemo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        // Validasi Status: hanya boleh jika status memo lama sebelum pengiriman (DALAM_PENGIRIMAN atau setelahnya dilarang)
        if (oldMemo.getStatusAkhir() == MemoStatus.DALAM_PENGIRIMAN || 
            oldMemo.getStatusAkhir() == MemoStatus.DITERIMA_USER || 
            oldMemo.getStatusAkhir() == MemoStatus.TERKIRIM_SEBAGIAN || 
            oldMemo.getStatusAkhir() == MemoStatus.SELESAI || 
            oldMemo.getStatusAkhir() == MemoStatus.DIBATALKAN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Memo yang sudah dalam pengiriman tidak dapat direvisi");
        }

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        // Buat nomor memo baru
        String newNomorMemo = "MEMO-" + oldMemo.getMemoType().toUpperCase() + "-" + System.currentTimeMillis();

        // Salin data header
        Memo newMemo = Memo.builder()
                .nomorMemo(newNomorMemo)
                .customer(oldMemo.getCustomer())
                .marketing(oldMemo.getMarketing())
                .marketingName(oldMemo.getMarketingName())
                .marketingEmpCode(oldMemo.getMarketingEmpCode())
                .creator(aktor)
                .tanggalMemo(LocalDateTime.now())
                .isTeknisRequired(oldMemo.getIsTeknisRequired())
                .isDeliveryRequired(oldMemo.getIsDeliveryRequired())
                .opsiPengiriman(oldMemo.getOpsiPengiriman())
                .tipeOngkir(oldMemo.getTipeOngkir())
                .metodePembayaran(oldMemo.getMetodePembayaran())
                .statusAkhir(MemoStatus.DRAFT) // Mulai sebagai DRAFT
                .totalHarga(oldMemo.getTotalHarga())
                .deskripsi(oldMemo.getDeskripsi())
                .memoType(oldMemo.getMemoType())
                .orderIdMarketplace(oldMemo.getOrderIdMarketplace() != null && !oldMemo.getOrderIdMarketplace().isEmpty() ? oldMemo.getOrderIdMarketplace() + "-REV-" + System.currentTimeMillis() : null)
                .resi(oldMemo.getResi())
                .ekspedisi(oldMemo.getEkspedisi())
                .subEkspedisi(oldMemo.getSubEkspedisi())
                .platform(oldMemo.getPlatform())
                .kodePos(oldMemo.getKodePos())
                .tempo(oldMemo.getTempo())
                .badanUsaha(oldMemo.getBadanUsaha())
                .jenisPrinter(oldMemo.getJenisPrinter())
                .revisedFromId(oldMemo.getId()) // Hubungkan ke memo lama
                .build();

        newMemo = memoRepository.save(newMemo);

        // Salin data detail (daftar barang)
        List<MemoItem> oldItems = memoItemRepository.findByMemoId(oldMemo.getId());
        for (MemoItem oldItem : oldItems) {
            MemoItem newItem = MemoItem.builder()
                    .memo(newMemo)
                    .namaBarang(oldItem.getNamaBarang())
                    .qty(oldItem.getQty())
                    .hargaSatuan(oldItem.getHargaSatuan())
                    .subtotal(oldItem.getSubtotal())
                    .status(oldItem.getStatus())
                    .catatanGudang(oldItem.getCatatanGudang())
                    .qtyShipped(0) // Default 0
                    .build();
            memoItemRepository.save(newItem);
        }

        // Pencatatan Log System pada Memo Baru
        memoLogRepository.save(new MemoLog(
                newMemo.getId(), 
                MemoStatus.DRAFT.name(), 
                aktor.getId(), 
                "Memo ini merupakan revisi dari Memo ID: " + oldMemo.getNomorMemo()
        ));

        // Audit Log Global
        activityLogService.log(username, "MEMO_DUPLICATE_REVISION", 
                "Melakukan duplikat dan revisi Memo " + oldMemo.getNomorMemo() + " ke Memo baru " + newMemo.getNomorMemo());

        sendMemoRefreshSignal();

        return WebResponse.<MemoDetailResponse>builder()
                .data(mapToDetailResponse(newMemo))
                .status(HttpStatus.OK.value())
                .message("Memo berhasil diduplikat untuk direvisi")
                .build();
    }

    @Transactional
    public WebResponse<MemoDetailResponse> duplicateHeader(UUID memoId, String username) {
        Memo oldMemo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        // Buat nomor memo baru
        String newNomorMemo = "MEMO-" + oldMemo.getMemoType().toUpperCase() + "-" + System.currentTimeMillis();

        // Salin data header saja (tanpa barang/detail kosong)
        Memo newMemo = Memo.builder()
                .nomorMemo(newNomorMemo)
                .customer(oldMemo.getCustomer())
                .marketing(oldMemo.getMarketing())
                .marketingName(oldMemo.getMarketingName())
                .marketingEmpCode(oldMemo.getMarketingEmpCode())
                .creator(aktor)
                .tanggalMemo(LocalDateTime.now())
                .isTeknisRequired(oldMemo.getIsTeknisRequired())
                .isDeliveryRequired(oldMemo.getIsDeliveryRequired())
                .opsiPengiriman(oldMemo.getOpsiPengiriman())
                .tipeOngkir(oldMemo.getTipeOngkir())
                .metodePembayaran(oldMemo.getMetodePembayaran())
                .statusAkhir(MemoStatus.DRAFT) // Mulai sebagai DRAFT
                .totalHarga(java.math.BigDecimal.ZERO) // Total harga 0 karena kosong
                .deskripsi(oldMemo.getDeskripsi())
                .memoType(oldMemo.getMemoType())
                .orderIdMarketplace(oldMemo.getOrderIdMarketplace() != null && !oldMemo.getOrderIdMarketplace().isEmpty() ? oldMemo.getOrderIdMarketplace() + "-DUP-" + System.currentTimeMillis() : null)
                .resi(oldMemo.getResi())
                .ekspedisi(oldMemo.getEkspedisi())
                .subEkspedisi(oldMemo.getSubEkspedisi())
                .platform(oldMemo.getPlatform())
                .kodePos(oldMemo.getKodePos())
                .tempo(oldMemo.getTempo())
                .badanUsaha(oldMemo.getBadanUsaha())
                .jenisPrinter(oldMemo.getJenisPrinter())
                .build();

        newMemo = memoRepository.save(newMemo);

        // Pencatatan Log System pada Memo Baru
        memoLogRepository.save(new MemoLog(
                newMemo.getId(), 
                MemoStatus.DRAFT.name(), 
                aktor.getId(), 
                "Memo dibuat menggunakan duplikat header dari Memo ID: " + oldMemo.getNomorMemo()
        ));

        // Audit Log Global
        activityLogService.log(username, "MEMO_DUPLICATE_HEADER", 
                "Melakukan duplikat header Memo " + oldMemo.getNomorMemo() + " ke Memo baru " + newMemo.getNomorMemo());

        sendMemoRefreshSignal();

        return WebResponse.<MemoDetailResponse>builder()
                .data(mapToDetailResponse(newMemo))
                .status(HttpStatus.OK.value())
                .message("Memo header berhasil diduplikat")
                .build();
    }

    private void checkAndApplyRevisionCancellation(Memo newMemo, User aktor) {
        if (newMemo.getRevisedFromId() != null) {
            Optional<Memo> oldMemoOpt = memoRepository.findById(newMemo.getRevisedFromId());
            if (oldMemoOpt.isPresent()) {
                Memo oldMemo = oldMemoOpt.get();
                if (oldMemo.getStatusAkhir() != MemoStatus.DIBATALKAN) {
                    oldMemo.setStatusAkhir(MemoStatus.DIBATALKAN);
                    oldMemo.setRevisionToId(newMemo.getId());
                    memoRepository.save(oldMemo);

                    // Pencatatan Log System pada Memo Lama
                    memoLogRepository.save(new MemoLog(
                            oldMemo.getId(), 
                            MemoStatus.DIBATALKAN.name(),
                            aktor.getId(), 
                            "Memo dibatalkan dan diganti dengan Memo baru ID: " + newMemo.getNomorMemo() + " via fitur Duplikat Revisi"
                    ));

                    // Pencatatan Log System pada Memo Baru
                    memoLogRepository.save(new MemoLog(
                            newMemo.getId(), 
                            newMemo.getStatusAkhir().name(),
                            aktor.getId(), 
                            "Memo dibuat sebagai duplikat revisi dari Memo lama: " + oldMemo.getNomorMemo()
                    ));

                    // Jika dibatalkan, hapus (soft-delete) juga semua jadwal yang terkait
                    List<PenjadwalanKonfirmasi> relatedJadwal = penjadwalanRepo.findByMemo_IdAndDeletedAtIsNull(oldMemo.getId());
                    for (PenjadwalanKonfirmasi jadwal : relatedJadwal) {
                        if (jadwal.getStatusJadwal() != StatusJadwal.SELESAI) {
                            jadwal.setStatusJadwal(StatusJadwal.DIBATALKAN);
                            penjadwalanRepo.save(jadwal);
                        }
                    }
                }
            }
        }
    }

    @Transactional
    public WebResponse<String> deleteMemo(UUID memoId, String username) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memo tidak ditemukan"));

        if (memo.getStatusAkhir() != MemoStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hanya memo DRAFT yang bisa dihapus");
        }

        User aktor = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User login tidak ditemukan"));

        boolean isCreator = memo.getCreator() != null && memo.getCreator().getId().equals(aktor.getId());
        boolean isSameRole = memo.getCreator() != null && memo.getCreator().getRole() == aktor.getRole();

        if (!isCreator && !isSameRole && aktor.getRole() != Role.ADMIN && !aktor.getRole().name().startsWith("SPV_") && aktor.getRole() != Role.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki hak untuk menghapus memo ini");
        }

        // Delete items first
        List<MemoItem> items = memoItemRepository.findByMemoId(memoId);
        memoItemRepository.deleteAll(items);
        
        // Delete logs
        List<MemoLog> logs = memoLogRepository.findByMemoIdOrderByCreatedAtDesc(memoId);
        memoLogRepository.deleteAll(logs);

        // Delete memo
        memoRepository.delete(memo);

        return WebResponse.<String>builder()
                .status(HttpStatus.OK.value())
                .message("Draf memo berhasil dihapus karena dibatalkan")
                .build();
    }

    public WebResponse<List<MemoDetailResponse>> searchByBarcode(String code, String name) {
        if (code == null || code.trim().isEmpty()) {
            return WebResponse.<List<MemoDetailResponse>>builder()
                    .data(List.of())
                    .status(HttpStatus.OK.value())
                    .message("Parameter kode tidak boleh kosong")
                    .build();
        }

        String trimmed = code.trim();

        // Priority 1: Exact match by resi
        List<Memo> memos = memoRepository.findByResiIgnoreCase(trimmed);
        if (memos.isEmpty()) {
            // Priority 2: Exact match by orderIdMarketplace
            memos = memoRepository.findByOrderIdMarketplaceIgnoreCase(trimmed);
        }
        if (memos.isEmpty()) {
            // Priority 3: Exact match by nomorMemo
            Optional<Memo> byNomorMemo = memoRepository.findByNomorMemo(trimmed);
            if (byNomorMemo.isPresent()) {
                memos = List.of(byNomorMemo.get());
            }
        }
        if (memos.isEmpty()) {
            // Priority 4: Partial match by resi (LIKE %code%)
            memos = memoRepository.findByResiIgnoreCaseContaining(trimmed);
        }
        if (memos.isEmpty()) {
            // Priority 5: Partial match by orderIdMarketplace (LIKE %code%)
            memos = memoRepository.findByOrderIdMarketplaceIgnoreCaseContaining(trimmed);
        }

        List<MemoDetailResponse> responseList = memos.stream()
                .map(memo -> toMemoDetailResponse(memo, name))
                .collect(Collectors.toList());

        return WebResponse.<List<MemoDetailResponse>>builder()
                .data(responseList)
                .status(HttpStatus.OK.value())
                .build();
    }

    public WebResponse<List<MemoDetailResponse>> searchByResi(String resi, String name) {
        if (resi == null || resi.trim().isEmpty()) {
            return WebResponse.<List<MemoDetailResponse>>builder()
                    .data(List.of())
                    .status(HttpStatus.OK.value())
                    .message("Parameter resi tidak boleh kosong")
                    .build();
        }

        List<Memo> memos = memoRepository.findByResiIgnoreCaseContaining(resi.trim());

        List<MemoDetailResponse> responseList = memos.stream()
                .map(memo -> toMemoDetailResponse(memo, name))
                .collect(Collectors.toList());

        return WebResponse.<List<MemoDetailResponse>>builder()
                .data(responseList)
                .status(HttpStatus.OK.value())
                .build();
    }

    public WebResponse<List<MemoDetailResponse>> searchByOrderId(String orderId, String name) {
        if (orderId == null || orderId.trim().isEmpty()) {
            return WebResponse.<List<MemoDetailResponse>>builder()
                    .data(List.of())
                    .status(HttpStatus.OK.value())
                    .message("Parameter orderId tidak boleh kosong")
                    .build();
        }

        List<Memo> memos = memoRepository.findByOrderIdMarketplaceIgnoreCaseContaining(orderId.trim());

        List<MemoDetailResponse> responseList = memos.stream()
                .map(memo -> toMemoDetailResponse(memo, name))
                .collect(Collectors.toList());

        return WebResponse.<List<MemoDetailResponse>>builder()
                .data(responseList)
                .status(HttpStatus.OK.value())
                .build();
    }

    private MemoDetailResponse toMemoDetailResponse(Memo memo, String username) {
        List<MemoItemResponse> itemResponses = memoItemRepository.findByMemoId(memo.getId())
                .stream()
                .map(item -> MemoItemResponse.builder()
                        .id(item.getId())
                        .namaBarang(item.getNamaBarang())
                        .qty(item.getQty())
                        .qtyShipped(item.getQtyShipped())
                        .hargaSatuan(item.getHargaSatuan())
                        .subtotal(item.getSubtotal())
                        .status(item.getStatus())
                        .catatanGudang(item.getCatatanGudang())
                        .build()
                )
                .collect(Collectors.toList());

        List<PenjadwalanResponse> jadwalResponses = penjadwalanRepo.findByMemo_IdAndDeletedAtIsNull(memo.getId())
                .stream()
                .map(this::mapToPenjadwalanResponse)
                .collect(Collectors.toList());

        return MemoDetailResponse.builder()
                .id(memo.getId())
                .nomorMemo(memo.getNomorMemo())
                .customerId(memo.getCustomer() != null ? memo.getCustomer().getId() : null)
                .pelangganMybizId(memo.getPelangganMybiz() != null ? memo.getPelangganMybiz().getId() : null)
                .customerName(memo.getPelangganMybiz() != null ? memo.getPelangganMybiz().getNamaPartner() :
                        (memo.getCustomer() != null ? memo.getCustomer().getNamaPelanggan() : null))
                .customerPhone(memo.getPelangganMybiz() != null ? memo.getPelangganMybiz().getNoTelepon() :
                        (memo.getCustomer() != null ? memo.getCustomer().getNoHp() : null))
                .tanggalMemo(memo.getTanggalMemo())
                .totalHarga(memo.getTotalHarga())
                .deskripsi(memo.getDeskripsi())
                .statusAkhir(memo.getStatusAkhir())
                .isTeknisRequired(memo.getIsTeknisRequired())
                .isDeliveryRequired(memo.getIsDeliveryRequired())
                .marketingName(memo.getMarketingName())
                .marketingUsername(memo.getMarketing() != null ? memo.getMarketing().getUsername() : null)
                .marketingEmpCode(memo.getMarketingEmpCode())
                .metodePembayaran(memo.getMetodePembayaran())
                .memoType(memo.getMemoType())
                .orderIdMarketplace(memo.getOrderIdMarketplace())
                .resi(memo.getResi())
                .ekspedisi(memo.getEkspedisi())
                .subEkspedisi(memo.getSubEkspedisi())
                .platform(memo.getPlatform())
                .kodePos(memo.getKodePos())
                .tempo(memo.getTempo())
                .badanUsaha(memo.getBadanUsaha())
                .nomorJl(memo.getNomorJl())
                .opsiPengiriman(memo.getOpsiPengiriman())
                .tipeOngkir(memo.getTipeOngkir())
                .estimasiOngkir(memo.getEstimasiOngkir())
                .items(itemResponses)
                .penjadwalanHistory(jadwalResponses)
                .build();
    }
}
