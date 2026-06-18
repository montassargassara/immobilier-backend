package com.immobilier.backend.controller;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.security.CustomUserDetails;
import com.immobilier.backend.service.AffiliateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints used by the affiliate themselves (own dashboard, offers, stats).
 * Public registration endpoint is also here.
 */
@Slf4j
@RestController
@RequestMapping("/api/affiliate")
@RequiredArgsConstructor
public class AffiliateController {

    private final AffiliateService affiliateService;

    // ── Public: registration ──────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<AffiliateProfileDTO> register(@Valid @RequestBody CreateAffiliateRequest request) {
        log.info("New affiliate registration: {}", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(affiliateService.registerAffiliate(request));
    }

    // ── My profile ────────────────────────────────────────────────────────────

    @GetMapping("/my-profile")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<AffiliateProfileDTO> getMyProfile() {
        return ResponseEntity.ok(affiliateService.getMyProfile(currentUserId()));
    }

    // ── Eligible properties in my zones ──────────────────────────────────────

    @GetMapping("/properties")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<List<AffiliatePropertyDTO>> getEligibleProperties() {
        return ResponseEntity.ok(affiliateService.getEligiblePropertiesForAffiliate(currentUserId()));
    }

    // ── Region management ─────────────────────────────────────────────────────

    @GetMapping("/regions")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<List<AffiliateRegionDTO>> getMyRegions() {
        return ResponseEntity.ok(affiliateService.getRegions(currentUserId()));
    }

    @PostMapping("/add-zone")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<AffiliateRegionDTO> addZone(@RequestBody AddZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(affiliateService.addZone(currentUserId(), request));
    }

    @DeleteMapping("/remove-zone/{regionId}")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<Void> removeZone(@PathVariable Long regionId) {
        affiliateService.removeZone(currentUserId(), regionId);
        return ResponseEntity.noContent().build();
    }

    // ── Activity tracking ─────────────────────────────────────────────────────

    @PostMapping("/track")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<Void> trackActivity(
            @RequestParam String activityType,
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) String metadata) {
        affiliateService.trackActivity(currentUserId(), activityType, propertyId, metadata);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/activities")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<List<AffiliateActivityDTO>> getMyActivities() {
        return ResponseEntity.ok(affiliateService.getActivities(currentUserId()));
    }

    // ── Stats & ranking ───────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<AffiliateStatsDTO> getMyStats() {
        return ResponseEntity.ok(affiliateService.getStats(currentUserId()));
    }

    @GetMapping("/ranking")
    @PreAuthorize("hasAnyRole('AFFILIATE', 'SUPER_ADMIN', 'ADMIN', 'RESPONSABLE_COMMERCIAL')")
    public ResponseEntity<List<AffiliateStatsDTO>> getMonthlyRanking() {
        return ResponseEntity.ok(affiliateService.getMonthlyRanking());
    }

    @GetMapping("/my-ranking")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<AffiliateStatsDTO> getMyRanking() {
        return ResponseEntity.ok(affiliateService.getMyRankingPosition(currentUserId()));
    }

    // ── Suggested expansion zones ─────────────────────────────────────────────

    @GetMapping("/suggested-zones")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<List<SuggestedZoneDTO>> getSuggestedZones() {
        return ResponseEntity.ok(affiliateService.getSuggestedZones(currentUserId()));
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<List<AffiliateTransactionDTO>> getMyTransactions() {
        return ResponseEntity.ok(affiliateService.getTransactions(currentUserId()));
    }

    // ── Security context helper ───────────────────────────────────────────────

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getUserId();
        }
        throw new RuntimeException("User not authenticated");
    }
}
