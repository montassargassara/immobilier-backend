package com.immobilier.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AffiliateTransactionDTO {
    private Long id;
    private Long affiliateId;
    private String affiliateName;
    private Long propertyId;
    private String propertyTitle;
    private LocalDateTime transactionDate;
    private Double propertyPrice;
    private Double commissionPercentage;
    private Double commissionAmount;
    private String transactionType;
    private String clientEmail;
    private String buyerName;     // real buyer brought by the affiliate (CRM lead, no User)
    private Boolean viaAffiliate; // true when this sale originated from an affiliate offer
    private Boolean isPaid;
    private LocalDateTime paymentDate;
}
