package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BILocationKpiDTO {
    private long activeRentals;
    private long expiringIn30Days;
    private long totalLocationProperties;
    private double currentMonthRevenue;
    private double previousMonthRevenue;
    private double revenueTrend;
    private double annualProjectedRevenue;
    private double averageMonthlyRevenue;
    private double occupancyRate;
}
