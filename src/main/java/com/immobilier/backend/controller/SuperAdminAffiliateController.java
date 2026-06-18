package com.immobilier.backend.controller;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.security.CustomUserDetails;
import com.immobilier.backend.service.AffiliateService;
import com.immobilier.backend.service.MonthlyBonusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Super Admin endpoints for managing affiliates, approvals, rankings and payouts.
 * All methods require SUPER_ADMIN role.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/affiliates")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminAffiliateController {

    private final AffiliateService affiliateService;
    private final MonthlyBonusService monthlyBonusService;

    // ── Affiliate list ────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<AffiliateProfileDTO>> getAllAffiliates() {
        return ResponseEntity.ok(affiliateService.getAllAffiliates());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<AffiliateProfileDTO>> getPendingAffiliates() {
        return ResponseEntity.ok(affiliateService.getPendingAffiliates());
    }

    @GetMapping("/{affiliateId}")
    public ResponseEntity<AffiliateProfileDTO> getAffiliate(@PathVariable Long affiliateId) {
        return ResponseEntity.ok(affiliateService.getAffiliateById(affiliateId));
    }

    @GetMapping("/{affiliateId}/stats")
    public ResponseEntity<AffiliateStatsDTO> getAffiliateStats(@PathVariable Long affiliateId) {
        return ResponseEntity.ok(affiliateService.getStats(affiliateId));
    }

    // ── Approval workflow ─────────────────────────────────────────────────────

    @PutMapping("/{affiliateId}/approve")
    public ResponseEntity<AffiliateProfileDTO> approve(@PathVariable Long affiliateId) {
        log.info("Super Admin {} approving affiliate {}", currentUserId(), affiliateId);
        return ResponseEntity.ok(affiliateService.approveAffiliate(affiliateId, currentUserId()));
    }

    @PutMapping("/{affiliateId}/reject")
    public ResponseEntity<AffiliateProfileDTO> reject(
            @PathVariable Long affiliateId,
            @RequestBody(required = false) AffiliateApprovalRequest request) {
        String reason = request != null ? request.getReason() : null;
        log.info("Super Admin {} rejecting affiliate {}", currentUserId(), affiliateId);
        return ResponseEntity.ok(affiliateService.rejectAffiliate(affiliateId, reason, currentUserId()));
    }

    @PutMapping("/{affiliateId}/suspend")
    public ResponseEntity<AffiliateProfileDTO> suspend(
            @PathVariable Long affiliateId,
            @RequestBody(required = false) AffiliateApprovalRequest request) {
        String reason = request != null ? request.getReason() : null;
        log.info("Super Admin {} suspending affiliate {}", currentUserId(), affiliateId);
        return ResponseEntity.ok(affiliateService.suspendAffiliate(affiliateId, reason, currentUserId()));
    }

    @PutMapping("/{affiliateId}/activate")
    public ResponseEntity<AffiliateProfileDTO> activate(@PathVariable Long affiliateId) {
        log.info("Super Admin {} re-activating affiliate {}", currentUserId(), affiliateId);
        return ResponseEntity.ok(affiliateService.activateAffiliate(affiliateId, currentUserId()));
    }

    // ── Region management ─────────────────────────────────────────────────────

    @GetMapping("/{affiliateId}/regions")
    public ResponseEntity<List<AffiliateRegionDTO>> getRegions(@PathVariable Long affiliateId) {
        return ResponseEntity.ok(affiliateService.getRegions(affiliateId));
    }

    @PostMapping("/{affiliateId}/regions")
    public ResponseEntity<AffiliateRegionDTO> addRegion(
            @PathVariable Long affiliateId,
            @Valid @RequestBody RegionSelection request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(affiliateService.addRegion(affiliateId, request));
    }

    @PutMapping("/{affiliateId}/regions/{regionId}")
    public ResponseEntity<AffiliateRegionDTO> updateRegion(
            @PathVariable Long affiliateId,
            @PathVariable Long regionId,
            @RequestBody RegionSelection request) {
        return ResponseEntity.ok(affiliateService.updateRegion(affiliateId, regionId, request));
    }

    @DeleteMapping("/{affiliateId}/regions/{regionId}")
    public ResponseEntity<Void> removeRegion(
            @PathVariable Long affiliateId,
            @PathVariable Long regionId) {
        affiliateService.removeRegion(affiliateId, regionId);
        return ResponseEntity.noContent().build();
    }

    // ── Ranking ───────────────────────────────────────────────────────────────

    @GetMapping("/ranking")
    public ResponseEntity<List<AffiliateStatsDTO>> getMonthlyRanking() {
        return ResponseEntity.ok(affiliateService.getMonthlyRanking());
    }

    // ── Bonus management ──────────────────────────────────────────────────────

    /**
     * Calculate and persist bonuses for a given month/year.
     * Defaults to previous month if no params supplied.
     */
    @PostMapping("/bonuses/calculate")
    public ResponseEntity<List<MonthlyBonusDTO>> calculateBonuses(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        LocalDate ref = LocalDate.now().minusMonths(1);
        int m = month != null ? month : ref.getMonthValue();
        int y = year  != null ? year  : ref.getYear();
        log.info("Super Admin {} calculating bonuses for {}/{}", currentUserId(), m, y);
        return ResponseEntity.ok(monthlyBonusService.calculateAndSaveMonthlyBonuses(m, y));
    }

    @GetMapping("/bonuses")
    public ResponseEntity<List<MonthlyBonusDTO>> getBonuses(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        LocalDate now = LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int y = year  != null ? year  : now.getYear();
        return ResponseEntity.ok(monthlyBonusService.getBonusesForMonth(m, y));
    }

    @GetMapping("/{affiliateId}/bonuses")
    public ResponseEntity<List<MonthlyBonusDTO>> getAffiliateBonusHistory(@PathVariable Long affiliateId) {
        return ResponseEntity.ok(monthlyBonusService.getBonusHistoryForAffiliate(affiliateId));
    }

    @PutMapping("/bonuses/{bonusId}/pay")
    public ResponseEntity<MonthlyBonusDTO> markRewardPaid(@PathVariable Long bonusId) {
        log.info("Super Admin {} marking reward {} as paid", currentUserId(), bonusId);
        return ResponseEntity.ok(monthlyBonusService.markRewardPaid(bonusId));
    }

    // ── Transactions / payouts ────────────────────────────────────────────────

    @GetMapping("/transactions")
    public ResponseEntity<List<AffiliateTransactionDTO>> getAllTransactions() {
        return ResponseEntity.ok(affiliateService.getAllTransactions());
    }

    @GetMapping("/{affiliateId}/transactions")
    public ResponseEntity<List<AffiliateTransactionDTO>> getAffiliateTransactions(@PathVariable Long affiliateId) {
        return ResponseEntity.ok(affiliateService.getTransactions(affiliateId));
    }

    @PutMapping("/transactions/{transactionId}/pay")
    public ResponseEntity<AffiliateTransactionDTO> markCommissionPaid(@PathVariable Long transactionId) {
        log.info("Super Admin {} marking commission paid for transaction {}", currentUserId(), transactionId);
        return ResponseEntity.ok(affiliateService.markCommissionPaid(transactionId));
    }

    // ── Affiliate-brought customers (CRM leads — never User accounts) ──────────
    // Literal "/customers" is matched before the "/{affiliateId}" path variable.

    @GetMapping("/customers")
    public ResponseEntity<List<AffiliateCustomerDTO>> getAllAffiliateCustomers() {
        return ResponseEntity.ok(affiliateService.getAllAffiliateCustomers());
    }

    @GetMapping("/{affiliateId}/customers")
    public ResponseEntity<List<AffiliateCustomerDTO>> getCustomersForAffiliate(@PathVariable Long affiliateId) {
        return ResponseEntity.ok(affiliateService.getCustomersForAffiliate(affiliateId));
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
