package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BIKpiDTO {
    private long totalProperties;
    private long disponibleCount;
    private long venduCount;
    private long loueCount;
    private long agencyCount;
    private long affiliateCount;
    private long clientCount;
    private double totalRevenue;
    private double currentMonthRevenue;
    private double previousMonthRevenue;
    private double totalCommissions;
    private double currentMonthCommissions;
    private double conversionRate;
    private double revenueTrend;
    private double salesTrend;
    private double commissionsTrend;
    private long currentMonthSales;
    private long previousMonthSales;
    private long stagnantProperties;
}
