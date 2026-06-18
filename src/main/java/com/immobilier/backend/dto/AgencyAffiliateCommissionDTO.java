package com.immobilier.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgencyAffiliateCommissionDTO {
    private Long transactionId;
    private String affiliatePrenom;
    private String affiliateNom;
    private String propertyTitre;
    private String propertyAdresse;
    private String propertyCity;
    private Double propertyPrice;
    private Double commissionPercentage;
    private Double commissionAmount;
    private String transactionType;
    private LocalDateTime transactionDate;
    private Boolean isPaid;
    private String clientEmail;
}
