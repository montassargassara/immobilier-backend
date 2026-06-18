package com.immobilier.backend.controller;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.service.BIService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bi")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class BIController {

    private final BIService biService;

    @GetMapping("/kpis")
    public ResponseEntity<BIKpiDTO> getKpis() {
        return ResponseEntity.ok(biService.getKpis());
    }

    @GetMapping("/trends")
    public ResponseEntity<BITrendDTO> getTrends() {
        return ResponseEntity.ok(biService.getTrends());
    }

    @GetMapping("/revenue-breakdown")
    public ResponseEntity<BIRevenueBreakdownDTO> getRevenueBreakdown() {
        return ResponseEntity.ok(biService.getRevenueBreakdown());
    }

    @GetMapping("/commission-breakdown")
    public ResponseEntity<BICommissionDTO> getCommissionBreakdown() {
        return ResponseEntity.ok(biService.getCommissionBreakdown());
    }

    @GetMapping("/staff-ranking")
    public ResponseEntity<List<BIStaffRankDTO>> getStaffRanking(
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(biService.getStaffRanking(limit));
    }

    @GetMapping("/affiliate-impact")
    public ResponseEntity<BIAffiliateImpactDTO> getAffiliateImpact() {
        return ResponseEntity.ok(biService.getAffiliateImpact());
    }

    @GetMapping("/top-cities")
    public ResponseEntity<List<BITopCityDTO>> getTopCities(
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(biService.getTopCities(limit));
    }

    @GetMapping("/type-breakdown")
    public ResponseEntity<List<BITypeBreakdownDTO>> getTypeBreakdown() {
        return ResponseEntity.ok(biService.getTypeBreakdown());
    }

    @GetMapping("/agency-ranking")
    public ResponseEntity<List<BIAgencyRankDTO>> getAgencyRanking(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(biService.getAgencyRanking(limit));
    }

    @GetMapping("/affiliate-ranking")
    public ResponseEntity<List<BIAffiliateRankDTO>> getAffiliateRanking(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(biService.getAffiliateRanking(limit));
    }

    @GetMapping("/insights")
    public ResponseEntity<List<BIInsightDTO>> getInsights() {
        return ResponseEntity.ok(biService.getInsights());
    }

    @GetMapping("/location/kpis")
    public ResponseEntity<BILocationKpiDTO> getRentalKpis() {
        return ResponseEntity.ok(biService.getRentalKpis());
    }

    @GetMapping("/location/trends")
    public ResponseEntity<BILocationTrendDTO> getRentalTrends() {
        return ResponseEntity.ok(biService.getRentalTrends());
    }
}
