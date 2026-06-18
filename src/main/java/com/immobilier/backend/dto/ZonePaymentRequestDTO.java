package com.immobilier.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ZonePaymentRequestDTO {
    private Long id;
    private Long affiliateId;
    private String affiliateName;
    private String affiliateEmail;
    private String country;
    private String city;
    private String zoneName;
    private Double amount;
    private Boolean isPremium;
    private String proofImageUrl;
    private String status;
    private String rejectionReason;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
