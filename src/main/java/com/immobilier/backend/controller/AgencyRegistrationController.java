package com.immobilier.backend.controller;

import com.immobilier.backend.dto.AgencyApplicationDTO;
import com.immobilier.backend.dto.CreateAgencyRequest;
import com.immobilier.backend.service.AgencyRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AgencyRegistrationController {

    private final AgencyRegistrationService agencyRegistrationService;

    // ── Public self-registration ─────────────────────────────────────────────

    @PostMapping("/api/register/agency")
    public ResponseEntity<?> register(@Valid @RequestBody CreateAgencyRequest request) {
        try {
            AgencyApplicationDTO result = agencyRegistrationService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Super Admin management ───────────────────────────────────────────────

    @GetMapping("/api/admin/agencies/pending")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<AgencyApplicationDTO>> getPending() {
        return ResponseEntity.ok(agencyRegistrationService.getPendingApplications());
    }

    @GetMapping("/api/admin/agencies")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<AgencyApplicationDTO>> getAll() {
        return ResponseEntity.ok(agencyRegistrationService.getAllApplications());
    }

    @PutMapping("/api/admin/agencies/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(agencyRegistrationService.approve(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/admin/agencies/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                    @RequestBody Map<String, String> body) {
        try {
            String reason = body.getOrDefault("reason", "");
            return ResponseEntity.ok(agencyRegistrationService.reject(id, reason));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
