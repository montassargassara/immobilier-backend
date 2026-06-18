package com.immobilier.backend.controller;

import com.immobilier.backend.dto.ZonePaymentRequestDTO;
import com.immobilier.backend.security.CustomUserDetails;
import com.immobilier.backend.service.ZonePaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/zone-payments")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminZonePaymentController {

    private final ZonePaymentService zonePaymentService;

    @GetMapping
    public ResponseEntity<List<ZonePaymentRequestDTO>> getAll() {
        return ResponseEntity.ok(zonePaymentService.getAllRequests());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<ZonePaymentRequestDTO>> getPending() {
        return ResponseEntity.ok(zonePaymentService.getPendingRequests());
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ZonePaymentRequestDTO> approve(@PathVariable Long id) {
        return ResponseEntity.ok(zonePaymentService.approveRequest(id, currentUserId()));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ZonePaymentRequestDTO> reject(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "");
        return ResponseEntity.ok(zonePaymentService.rejectRequest(id, currentUserId(), reason));
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getUserId();
        }
        throw new RuntimeException("User not authenticated");
    }
}
