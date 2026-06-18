package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Real impact of the affiliate network on the platform.
 * All figures are computed from AffiliateCustomerRelation + AffiliateTransaction —
 * no fake data, no User accounts involved for the brought buyers.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BIAffiliateImpactDTO {
    private long   clientsBrought;        // distinct affiliate-brought CRM leads
    private long   salesViaAffiliates;    // affiliates only operate on VENTE (no rentals)
    private double revenueViaAffiliates;  // property value transacted via affiliates
    private double commissionsPaid;
    private double commissionsPending;
}
