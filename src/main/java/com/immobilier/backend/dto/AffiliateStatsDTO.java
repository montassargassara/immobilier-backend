package com.immobilier.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AffiliateStatsDTO {
    private Long affiliateId;
    private String affiliateName;

    private List<AffiliateRegionDTO> regions;

    // Sales / activities
    private Integer totalSales;
    private Integer totalViews;
    private Integer totalShares;
    private Integer totalContacts;
    private Integer totalVisits;

    // Revenue
    private Double totalRevenue;
    private Double pendingCommission;
    private Double paidCommission;

    // Offer stats
    private Integer totalOffersPending;
    private Integer totalOffersAccepted;
    private Integer totalOffersRejected;

    // Ranking
    private Double conversionRate;
    private Integer rank;
    private LocalDateTime lastActivity;

    // Monthly reward (TND) for this ranking period — null if not in top 3
    private Double monthlyRewardAmount;
}
