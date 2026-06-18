package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One commission line for the pending / paid history tables.
 * Unifies AffiliateTransaction and Commission rows.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CommissionRowDTO {
    private Long          id;
    private String        source;          // AFFILIATE | AGENCY | STAFF
    private String        beneficiaryName;
    private String        beneficiaryEmail;
    private Long          propertyId;
    private String        propertyTitle;
    private String        transactionType; // SALE | RENT
    private double        propertyPrice;
    private double        commissionAmount;
    private boolean       paid;
    private LocalDateTime date;
}
