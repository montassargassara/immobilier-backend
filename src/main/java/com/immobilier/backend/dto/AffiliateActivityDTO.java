package com.immobilier.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AffiliateActivityDTO {
    private Long id;
    private Long affiliateId;
    private String affiliateName;
    private String activityType;
    private Long propertyId;
    private String propertyTitle;
    private LocalDateTime activityDate;
    private String metadata;
}
