package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated totals for a commission scope (agency or staff),
 * used by the dedicated commission pages and the dashboard cards.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CommissionSummaryDTO {
    private double total;     // paid + pending
    private double paid;
    private double pending;
    private long   count;
}
