package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-commercial performance, aggregated exclusively from the unified
 * {@link com.immobilier.backend.entity.Commission} domain (beneficiaryType="STAFF").
 *
 * Every figure is sourced from real validated/finalized transactions —
 * a STAFF commission only exists after an ADMIN/SUPER_ADMIN approves the sale.
 * No fake data, no frontend recompute.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CommercialPerformanceDTO {
    private Long    commercialId;
    private String  name;
    private String  email;
    private String  role;              // COMMERCIAL | RESPONSABLE_COMMERCIAL
    private String  agencyName;
    private boolean active;

    private double  commissionRate;     // User.commissionRate (%)

    private long    salesCount;         // finalized VENDU brokered
    private long    rentalsCount;       // finalized LOUE brokered
    private long    dealsClosed;        // total validated transactions (sales + rentals)

    private double  revenueGenerated;   // Σ property price of those transactions (CA)
    private double  revenueThisMonth;   // commission earned in the current calendar month

    private double  commissionsEarned;  // total (paid + pending)
    private double  commissionsPaid;
    private double  commissionsPending;
}
