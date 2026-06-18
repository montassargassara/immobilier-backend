package com.immobilier.backend.controller;

import com.immobilier.backend.dto.ConvertLeadRequest;
import com.immobilier.backend.dto.InterestRequestDTO;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.security.SecurityUtils;
import com.immobilier.backend.service.InterestRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-side CRM endpoints for interest/lead management.
 * Placed at /api/admin/interests/ so the JWT interceptor sends the admin token.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/interests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
public class AdminInterestController {

    private final InterestRequestService interestRequestService;
    private final SecurityUtils securityUtils;

    /** All leads where the current user is the property owner. */
    @GetMapping("/my-leads")
    public ResponseEntity<List<InterestRequestDTO>> myLeads() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(interestRequestService.getInterestsForOwner(user));
    }

    /**
     * Move a lead through non-terminal pipeline steps
     * (PENDING → CONTACTED → VISITE_PROGRAMMEE → EN_NEGOCIATION).
     * Terminal transitions go through PUT /{id}/convert.
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        try {
            String newStatus = body.get("status");
            if (newStatus == null || newStatus.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Statut manquant"));
            }
            User user = securityUtils.getCurrentUser();
            InterestRequestDTO dto = interestRequestService.updateStatus(id, newStatus, user);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Convert or refuse a lead (terminal transition).
     * <ul>
     *   <li>CONVERTI_VENTE — property → VENDU, siblings auto-refused</li>
     *   <li>CONVERTI_LOCATION — property → LOUE + rental contract stored, siblings auto-refused</li>
     *   <li>REFUSE — lead locked, client notified</li>
     * </ul>
     */
    @PutMapping("/{id}/convert")
    public ResponseEntity<?> convert(@PathVariable Long id,
                                     @RequestBody ConvertLeadRequest req) {
        try {
            User user = securityUtils.getCurrentUser();
            InterestRequestDTO dto = interestRequestService.convertLead(id, req, user);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }
}
