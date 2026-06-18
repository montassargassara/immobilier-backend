package com.immobilier.backend.controller;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.security.SecurityUtils;
import com.immobilier.backend.service.AgencyAffiliateService;
import com.immobilier.backend.service.DashboardVisibilityService;
import com.immobilier.backend.service.PropertyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardVisibilityService dashboardVisibilityService;
    private final PropertyService propertyService;
    private final SecurityUtils securityUtils;
    private final AgencyAffiliateService agencyAffiliateService;

    /**
     * GET /api/dashboard/client-count
     * Retourne le nombre de clients visibles pour l'utilisateur connecté
     */
    @GetMapping("/client-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ClientCountDTO> getClientCount() {
        ClientCountDTO count = dashboardVisibilityService.getVisibleClientsCount();
        return ResponseEntity.ok(count);
    }

    /**
     * GET /api/dashboard/recent-clients
     * Retourne les clients récents visibles pour l'utilisateur connecté
     */
    @GetMapping("/recent-clients")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RecentClientsDTO>> getRecentClients(
            @RequestParam(defaultValue = "6") int limit) {
        List<RecentClientsDTO> recentClients = dashboardVisibilityService.getRecentVisibleClients(limit);
        return ResponseEntity.ok(recentClients);
    }

    /**
     * GET /api/dashboard/validations?limit=6
     * Role-based validation items:
     * - SUPER_ADMIN: recent AGENCY_OWNED properties (to review / message agencies)
     * - ADMIN/RESPONSABLE_COMMERCIAL/COMMERCIAL: visible properties recently added, newest first
     */
    @GetMapping("/validations")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PropertyListDTO>> getValidationItems(
            @RequestParam(defaultValue = "6") int limit) {
        User currentUser = securityUtils.getCurrentUser();
        List<PropertyListDTO> all = propertyService.getAllPropertiesListForUser(currentUser);

        List<PropertyListDTO> filtered;
        if (currentUser.getRole() == RoleType.SUPER_ADMIN) {
            filtered = all.stream()
                    .filter(p -> "AGENCY_OWNED".equals(p.getOwnerType()))
                    .sorted((a, b) -> {
                        if (b.getCreatedAt() == null) return -1;
                        if (a.getCreatedAt() == null) return 1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    })
                    .limit(limit)
                    .collect(Collectors.toList());
        } else {
            filtered = all.stream()
                    .sorted((a, b) -> {
                        if (b.getCreatedAt() == null) return -1;
                        if (a.getCreatedAt() == null) return 1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    })
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(filtered);
    }

    // ── Agency Affiliate Management ───────────────────────────────────────────

    /**
     * GET /api/dashboard/agency-affiliate-stats
     * Aggregate affiliation stats for the connected ADMIN's agency.
     */
    @GetMapping("/agency-affiliate-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgencyAffiliateStatsDTO> getAgencyAffiliateStats() {
        return ResponseEntity.ok(agencyAffiliateService.getAgencyStats());
    }

    /**
     * GET /api/dashboard/agency-affiliates
     * List affiliates who have submitted offers on this agency's properties.
     */
    @GetMapping("/agency-affiliates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AgencyAffiliateDTO>> getAgencyAffiliates() {
        return ResponseEntity.ok(agencyAffiliateService.getAffiliatesForAgency());
    }

    /**
     * GET /api/dashboard/agency-affiliate-commissions?limit=20
     * Recent completed commissions from this agency's properties.
     */
    @GetMapping("/agency-affiliate-commissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AgencyAffiliateCommissionDTO>> getAgencyAffiliateCommissions(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(agencyAffiliateService.getRecentCommissions(limit));
    }
}