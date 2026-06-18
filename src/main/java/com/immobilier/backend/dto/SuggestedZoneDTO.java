package com.immobilier.backend.dto;

import lombok.Data;

/**
 * Represents a geographic zone the affiliate doesn't cover yet but shows high opportunity.
 * Returned by GET /api/affiliate/suggested-zones.
 */
@Data
public class SuggestedZoneDTO {
    private String zoneName;
    private String country;
    private int propertyCount;
    private double averageCommission;
    private double averagePrice;
    /** Approximate demand score: number of accepted/completed sale offers in this zone. */
    private int demandScore;
    /** Composite opportunity score used for server-side ranking (not always sent to frontend). */
    private double opportunityScore;
    /** City part of the zone (for add-zone requests). */
    private String city;
    /** Price in TND to unlock this zone (0 = free first zone). */
    private double price;
    /** True when demandScore qualifies the zone as premium (100 TND). */
    private boolean isPremium;
}
