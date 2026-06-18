package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Commission intelligence aggregate for the BI dashboard.
 *
 * Combines the two commission sources:
 *  - affiliate commissions  → AffiliateTransaction
 *  - agency + staff          → Commission
 *
 * scope = "GLOBAL" (Super Admin) → every figure populated.
 * scope = "AGENCY" (Admin)       → only the caller's agency scope populated.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BICommissionDTO {

    private String scope;

    // Totals by beneficiary type
    private double affiliateTotal;
    private double agencyTotal;
    private double staffTotal;
    private double grandTotal;

    // Pending (unpaid)
    private double affiliatePending;
    private double agencyPending;
    private double staffPending;
    private double pendingTotal;
    private long   pendingCount;

    // Paid
    private double affiliatePaid;
    private double agencyPaid;
    private double staffPaid;
    private double paidTotal;
    private long   paidCount;
}
