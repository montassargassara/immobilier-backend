package com.immobilier.backend.service;

import com.immobilier.backend.dto.BIRevenueBreakdownDTO;
import com.immobilier.backend.repository.AffiliateTransactionRepository;
import com.immobilier.backend.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Centralized revenue computation. Single source of truth for the
 * gross / net + ownership-scope split shown on the BI dashboard.
 * BIService delegates here so the math lives in exactly one place.
 *
 * Revenue = completed sales (prixVente of VENDU) + completed rentals (prixLocation of LOUE).
 * Net     = gross − affiliate commissions attributable to that scope.
 */
@Service
@RequiredArgsConstructor
public class RevenueCalculator {

    private final PropertyRepository propertyRepository;
    private final AffiliateTransactionRepository affiliateTransactionRepository;

    public BIRevenueBreakdownDTO global() {
        double salesAll  = orZero(propertyRepository.calculateTotalRevenue());
        double rentalAll = orZero(propertyRepository.sumRentalRevenueAll());
        double globalGross = salesAll + rentalAll;

        double saGross = orZero(propertyRepository.sumSalesRevenueSuperAdminOwned())
                       + orZero(propertyRepository.sumRentalRevenueSuperAdminOwned());
        double agGross = orZero(propertyRepository.sumSalesRevenueAgencyOwned())
                       + orZero(propertyRepository.sumRentalRevenueAgencyOwned());

        double totalComm = orZero(affiliateTransactionRepository.getTotalCommissions());
        double saComm    = orZero(affiliateTransactionRepository.getTotalCommissionsSuperAdminOwned());
        double agComm    = orZero(affiliateTransactionRepository.getTotalCommissionsAgencyOwned());

        return BIRevenueBreakdownDTO.builder()
                .scope("GLOBAL")
                .globalGross(round1(globalGross))
                .globalNet(round1(globalGross - totalComm))
                .agencyGross(round1(agGross))
                .agencyNet(round1(agGross - agComm))
                .superAdminGross(round1(saGross))
                .superAdminNet(round1(saGross - saComm))
                .totalCommissions(round1(totalComm))
                .build();
    }

    public BIRevenueBreakdownDTO forAgency(Long adminId) {
        double agGross = orZero(propertyRepository.calculateTotalRevenueByAgencyAdmin(adminId))
                       + orZero(propertyRepository.calculateTotalRentalRevenueByAgencyAdmin(adminId));
        double agComm  = orZero(affiliateTransactionRepository.getTotalCommissionsByAgencyAdmin(adminId));

        return BIRevenueBreakdownDTO.builder()
                .scope("AGENCY")
                .globalGross(0).globalNet(0)
                .agencyGross(round1(agGross))
                .agencyNet(round1(agGross - agComm))
                .superAdminGross(0).superAdminNet(0)
                .totalCommissions(round1(agComm))
                .build();
    }

    private double orZero(Double v) { return v != null ? v : 0.0; }

    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
