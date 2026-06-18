package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Separated revenue view for the BI dashboard.
 *
 * Revenue = completed sales (prixVente of VENDU) + completed rentals (prixLocation of LOUE).
 * Net = gross − affiliate commissions attributable to that scope.
 *
 * scope = "GLOBAL"  → Super Admin: all three blocks are populated.
 * scope = "AGENCY"  → Admin: only the agency block is populated (others are 0).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BIRevenueBreakdownDTO {

    private String scope;

    /** All ownership combined. */
    private double globalGross;
    private double globalNet;

    /** Properties owned by agencies (ownerType AGENCY_OWNED or legacy NULL). */
    private double agencyGross;
    private double agencyNet;

    /** Properties owned directly by the Super Admin (ownerType SUPER_ADMIN_OWNED). */
    private double superAdminGross;
    private double superAdminNet;

    /** Total affiliate commissions deducted to obtain the net figures (within scope). */
    private double totalCommissions;
}
