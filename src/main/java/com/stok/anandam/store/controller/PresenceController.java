package com.stok.anandam.store.controller;

import com.stok.anandam.store.dto.UserResponse;
import com.stok.anandam.store.dto.UserSessionDto;
import com.stok.anandam.store.dto.WebResponse;
import com.stok.anandam.store.service.UserService;
import com.stok.anandam.store.service.UserSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/presence", "/presence"})
public class PresenceController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserSessionService userSessionService;

    @PostMapping("/heartbeat")
    public ResponseEntity<WebResponse<String>> heartbeat() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            userService.processHeartbeat(auth.getName());
        }

        return ResponseEntity.ok(WebResponse.<String>builder()
                .status(200)
                .message("Presence Heartbeat OK")
                .data("OK")
                .paging(null)
                .build());
    }

    @GetMapping("/users")
    public ResponseEntity<WebResponse<List<UserSessionDto>>> getActivePresenceUsers() {
        List<UserSessionDto> activeSessions = userSessionService.getAllActiveSessions();
        return ResponseEntity.ok(WebResponse.<List<UserSessionDto>>builder()
                .status(200)
                .message("Success fetch active presence sessions")
                .data(activeSessions)
                .paging(null)
                .build());
    }
}
