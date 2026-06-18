package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Personal performance snapshot for a COMMERCIAL / RESPONSABLE_COMMERCIAL.
 * Returned by GET /api/commissions/my-performance.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MyPerformanceDTO {
    private long   salesCount;       // SALE commissions
    private long   rentalsCount;     // RENT commissions
    private double totalCommission;
    private double pendingCommission;
    private double paidCommission;
    private double commissionRate;

    // 12-month commission trend (parallel arrays)
    private List<String> months;
    private List<Double> monthlyCommissions;
}
