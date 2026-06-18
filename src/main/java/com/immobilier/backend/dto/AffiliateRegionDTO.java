package com.immobilier.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AffiliateRegionDTO {
    private Long id;
    private Long affiliateId;
    private String affiliateName;
    private String regionName;
    private String country;
    private String city;
    private String regionDescription;
    private Boolean isActive;
    private Boolean isPaid;
    private Double pricePaid;
    private Boolean isPremium;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
