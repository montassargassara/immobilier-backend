package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class AgencyAffiliateStatsDTO {
    private int totalAffiliates;
    private int totalSalesViaAffiliation;
    private double totalCommissionsGlobal;
    private double totalCommissionsPaid;
    private double totalCommissionsPending;
    private int totalClientsApportes;
}
