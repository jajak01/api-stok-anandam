package com.stok.anandam.store.controller;

import com.stok.anandam.store.dto.PagingResponse;
import com.stok.anandam.store.dto.ItemSerialNumberResponse;
import com.stok.anandam.store.dto.SnVerificationResponse;
import com.stok.anandam.store.dto.WebResponse;
import com.stok.anandam.store.service.ItemSnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sn")
public class ItemSnController {

        @Autowired
        private ItemSnService itemSnService;

        /**
         * GET /api/sn/masuk
         * Filter: search (global), docId, user, itemName, sn, startDate, endDate
         * Sort: sortBy = tanggal | docId | user | itemName | sn, direction = asc | desc
         * Pagination: page, size
         */
        /**
         * GET /api/sn/verify?sn=...
         * Verifikasi SATU serial number (exact match) di SN Masuk (BL), SN Keluar
         * (JL), dan Data Warehouse (BL lama) sekaligus — satu round-trip.
         */
        @GetMapping("/verify")
        public ResponseEntity<WebResponse<SnVerificationResponse>> verifySn(
                        @RequestParam(name = "sn") String sn) {
                SnVerificationResponse data = itemSnService.verifyExact(sn);

                return ResponseEntity.ok(WebResponse.<SnVerificationResponse>builder()
                                .status(200)
                                .message("Success Verify SN")
                                .data(data)
                                .build());
        }

        @GetMapping("/masuk")
        public ResponseEntity<WebResponse<List<ItemSerialNumberResponse>>> getSnMasuk(
                        @RequestParam(name = "search", required = false) String search,
                        @RequestParam(name = "docId", required = false) String docId,
                        @RequestParam(name = "user", required = false) String user,
                        @RequestParam(name = "itemName", required = false) String itemName,
                        @RequestParam(name = "sn", required = false) String sn,
                        @RequestParam(name = "startDate", required = false) String startDate,
                        @RequestParam(name = "endDate", required = false) String endDate,
                        @RequestParam(name = "sortBy", defaultValue = "tanggal") String sortBy,
                        @RequestParam(name = "direction", defaultValue = "desc") String direction,
                        @RequestParam(name = "size", defaultValue = "10") int size,
                        @RequestParam(name = "page", defaultValue = "0") int page) {

                ItemSnService.SnPage pageResult = itemSnService.getSnDataPage(
                                "MASUK", search, docId, user, itemName, sn, startDate, endDate, sortBy, direction, size,
                                page * size);
                List<ItemSerialNumberResponse> data = pageResult.getItems();
                long totalItem = pageResult.getTotal();

                return ResponseEntity.ok(WebResponse.<List<ItemSerialNumberResponse>>builder()
                                .status(200)
                                .message("Success Fetch SN Masuk")
                                .data(data)
                                .paging(PagingResponse.builder()
                                                .currentPage(page)
                                                .totalPage((int) Math.ceil((double) totalItem / size))
                                                .size(size)
                                                .totalItem(totalItem)
                                                .build())
                                .build());
        }

        /**
         * GET /api/sn/keluar
         * Filter: search (global), docId, user, itemName, sn, startDate, endDate
         * Sort: sortBy = tanggal | docId | user | itemName | sn, direction = asc | desc
         * Pagination: page, size
         */
        @GetMapping("/keluar")
        public ResponseEntity<WebResponse<List<ItemSerialNumberResponse>>> getSnKeluar(
                        @RequestParam(name = "search", required = false) String search,
                        @RequestParam(name = "docId", required = false) String docId,
                        @RequestParam(name = "user", required = false) String user,
                        @RequestParam(name = "itemName", required = false) String itemName,
                        @RequestParam(name = "sn", required = false) String sn,
                        @RequestParam(name = "startDate", required = false) String startDate,
                        @RequestParam(name = "endDate", required = false) String endDate,
                        @RequestParam(name = "sortBy", defaultValue = "tanggal") String sortBy,
                        @RequestParam(name = "direction", defaultValue = "desc") String direction,
                        @RequestParam(name = "size", defaultValue = "10") int size,
                        @RequestParam(name = "page", defaultValue = "0") int page) {

                ItemSnService.SnPage pageResult = itemSnService.getSnDataPage(
                                "KELUAR", search, docId, user, itemName, sn, startDate, endDate, sortBy, direction,
                                size, page * size);
                List<ItemSerialNumberResponse> data = pageResult.getItems();
                long totalItem = pageResult.getTotal();

                return ResponseEntity.ok(WebResponse.<List<ItemSerialNumberResponse>>builder()
                                .status(200)
                                .message("Success Fetch SN Keluar")
                                .data(data)
                                .paging(PagingResponse.builder()
                                                .currentPage(page)
                                                .totalPage((int) Math.ceil((double) totalItem / size))
                                                .size(size)
                                                .totalItem(totalItem)
                                                .build())
                                .build());
        }
}