package com.stok.anandam.store.controller;

import com.stok.anandam.store.annotation.LogActivity;
import com.stok.anandam.store.dto.PagingResponse;
import com.stok.anandam.store.dto.UserRequest;
import com.stok.anandam.store.dto.UserResponse;
import com.stok.anandam.store.dto.WebResponse;
import com.stok.anandam.store.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

        @Autowired
        private UserService userService;

        @GetMapping
        public ResponseEntity<WebResponse<List<UserResponse>>> getAllUsers(
                        @RequestParam(name = "page", defaultValue = "0") int page,
                        @RequestParam(name = "size", defaultValue = "10") int size,
                        @RequestParam(name = "search", required = false) String search,
                        @RequestParam(name = "role", required = false) String role) {
                Page<UserResponse> usersPage = userService.getAllUsers(page, size, search, role);

                PagingResponse pagingResponse = PagingResponse.builder()
                                .currentPage(usersPage.getNumber())
                                .totalPage(usersPage.getTotalPages())
                                .size(size)
                                .totalItem(usersPage.getTotalElements())
                                .build();

                // Bungkus dalam WebResponse
                WebResponse<List<UserResponse>> response = WebResponse.<List<UserResponse>>builder()
                                .status(HttpStatus.OK.value())
                                .message("Success fetch users data")
                                .data(usersPage.getContent())
                                .paging(pagingResponse)
                                .build();

                return ResponseEntity.ok(response);
        }

        @PostMapping
        public ResponseEntity<WebResponse<UserResponse>> createUser(@Valid @RequestBody UserRequest request) {
                UserResponse userResponse = userService.createUser(request);

                WebResponse<UserResponse> response = WebResponse.<UserResponse>builder()
                                .status(HttpStatus.CREATED.value())
                                .message("User created successfully")
                                .data(userResponse)
                                .paging(null)
                                .build();

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @PutMapping("/{id}")
        @LogActivity("MENGUBAH DATA USER")
        public ResponseEntity<WebResponse<UserResponse>> updateUser(
                        @PathVariable(name = "id") Long id,
                        @Valid @RequestBody UserRequest request) {
                UserResponse updatedUser = userService.updateUser(id, request);

                WebResponse<UserResponse> response = WebResponse.<UserResponse>builder()
                                .status(HttpStatus.OK.value())
                                .message("User updated successfully")
                                .data(updatedUser)
                                .paging(null)
                                .build();

                return ResponseEntity.ok(response);
        }

        @DeleteMapping("/{id}")
        @LogActivity("MENGHAPUS USER")
        public ResponseEntity<WebResponse<String>> deleteUser(@PathVariable(name = "id") Long id) {

                userService.deleteUser(id);

                WebResponse<String> response = WebResponse.<String>builder()
                                .status(HttpStatus.OK.value())
                                .message("User berhasil dihapus")
                                .data(null)
                                .paging(null)
                                .build();

                return ResponseEntity.ok(response);
        }

        @PatchMapping("/{id}/toggle-status")
        @LogActivity("MENGUBAH STATUS AKTIF USER")
        public ResponseEntity<WebResponse<UserResponse>> toggleUserStatus(@PathVariable(name = "id") Long id) {
                UserResponse userResponse = userService.toggleUserStatus(id);

                WebResponse<UserResponse> response = WebResponse.<UserResponse>builder()
                                .status(HttpStatus.OK.value())
                                .message("User status toggled successfully")
                                .data(userResponse)
                                .paging(null)
                                .build();

                return ResponseEntity.ok(response);
        }

        @PostMapping("/{id}/clear-sessions")
        @LogActivity("BERSIHKAN SESI USER")
        public ResponseEntity<WebResponse<UserResponse>> clearUserSessions(@PathVariable(name = "id") Long id) {
                UserResponse userResponse = userService.clearUserSessions(id);

                WebResponse<UserResponse> response = WebResponse.<UserResponse>builder()
                                .status(HttpStatus.OK.value())
                                .message("Sesi user berhasil dibersihkan")
                                .data(userResponse)
                                .paging(null)
                                .build();

                return ResponseEntity.ok(response);
        }

        @GetMapping("/{id}/emp-scope")
        public ResponseEntity<WebResponse<java.util.Set<String>>> getUserEmpScope(@PathVariable(name = "id") Long id) {
                java.util.Set<String> scope = userService.getUserEmpScope(id);

                WebResponse<java.util.Set<String>> response = WebResponse.<java.util.Set<String>>builder()
                                .status(HttpStatus.OK.value())
                                .message("Success fetch user emp scope")
                                .data(scope)
                                .paging(null)
                                .build();

                return ResponseEntity.ok(response);
        }

        @PutMapping("/{id}/emp-scope")
        @LogActivity("UPDATE EMP SCOPE USER")
        public ResponseEntity<WebResponse<UserResponse>> updateUserEmpScope(
                        @PathVariable(name = "id") Long id,
                        @RequestBody java.util.Set<String> empScope) {
                UserResponse userResponse = userService.updateUserEmpScope(id, empScope);

                WebResponse<UserResponse> response = WebResponse.<UserResponse>builder()
                                .status(HttpStatus.OK.value())
                                .message("User emp scope updated successfully")
                                .data(userResponse)
                                .paging(null)
                                .build();

                return ResponseEntity.ok(response);
        }
}