package com.immobilier.backend.controller;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.security.SecurityUtils;
import com.immobilier.backend.service.PropertyShareRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/share-requests")
@RequiredArgsConstructor
public class PropertyShareRequestController {

    private final PropertyShareRequestService shareRequestService;
    private final SecurityUtils securityUtils;

    // ─── Super Admin: create share requests for a property ───────────────────

    @PostMapping("/property/{propertyId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<PropertyShareRequestDTO>> createRequests(
            @PathVariable Long propertyId,
            @Valid @RequestBody CreateShareRequestDTO dto) {

        User superAdmin = securityUtils.getCurrentUser();
        List<PropertyShareRequestDTO> result = shareRequestService.createShareRequests(propertyId, dto, superAdmin);
        return ResponseEntity.ok(result);
    }

    // ─── Super Admin: list all requests they sent ─────────────────────────────

    @GetMapping("/sent")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<PropertyShareRequestDTO>> getSentRequests() {
        User superAdmin = securityUtils.getCurrentUser();
        return ResponseEntity.ok(shareRequestService.getAllRequestsBySuperAdmin(superAdmin));
    }

    // ─── Super Admin: list requests for a specific property ──────────────────

    @GetMapping("/property/{propertyId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<PropertyShareRequestDTO>> getRequestsForProperty(
            @PathVariable Long propertyId) {

        User superAdmin = securityUtils.getCurrentUser();
        return ResponseEntity.ok(shareRequestService.getRequestsForProperty(propertyId, superAdmin));
    }

    // ─── Super Admin: agencies with share status for a property ──────────────

    @GetMapping("/property/{propertyId}/agencies")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<AgencyAdminDTO>> getAgenciesWithStatus(
            @PathVariable Long propertyId) {
        return ResponseEntity.ok(shareRequestService.getAgenciesWithShareStatus(propertyId));
    }

    // ─── Super Admin: cancel a pending request ────────────────────────────────

    @DeleteMapping("/{requestId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PropertyShareRequestDTO> cancelRequest(
            @PathVariable Long requestId) {

        User superAdmin = securityUtils.getCurrentUser();
        return ResponseEntity.ok(shareRequestService.cancelRequest(requestId, superAdmin));
    }

    // ─── Agency Admin: view all incoming requests ─────────────────────────────

    @GetMapping("/incoming")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<List<PropertyShareRequestDTO>> getIncomingRequests() {
        User agencyAdmin = resolveAgencyAdmin();
        return ResponseEntity.ok(shareRequestService.getRequestsForAgency(agencyAdmin));
    }

    // ─── Agency Admin: view pending requests only ─────────────────────────────

    @GetMapping("/incoming/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<List<PropertyShareRequestDTO>> getPendingIncomingRequests() {
        User agencyAdmin = resolveAgencyAdmin();
        return ResponseEntity.ok(shareRequestService.getPendingRequestsForAgency(agencyAdmin));
    }

    // ─── Any party: view single request ──────────────────────────────────────

    @GetMapping("/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PropertyShareRequestDTO> getRequest(@PathVariable Long requestId) {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(shareRequestService.getRequestById(requestId, user));
    }

    // ─── Agency Admin: respond (accept / reject) ──────────────────────────────

    @PutMapping("/{requestId}/respond")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PropertyShareRequestDTO> respond(
            @PathVariable Long requestId,
            @Valid @RequestBody ShareRequestResponseDTO dto) {

        User agencyAdmin = securityUtils.getCurrentUser();
        return ResponseEntity.ok(shareRequestService.respondToRequest(requestId, dto, agencyAdmin));
    }

    // ─── Helper: for non-ADMIN agency roles, find their ADMIN ────────────────

    private User resolveAgencyAdmin() {
        User user = securityUtils.getCurrentUser();
        if (user.getRole() == RoleType.ADMIN) return user;
        // Walk up the hierarchy to find ADMIN parent
        User current = user;
        while (current.getParent() != null) {
            if (current.getParent().getRole() == RoleType.ADMIN) return current.getParent();
            current = current.getParent();
        }
        return user; // fallback (shouldn't happen for valid hierarchy)
    }
}
