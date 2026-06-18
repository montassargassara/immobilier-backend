package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BIAgencyRankDTO {
    private long id;
    private String agencyName;
    private String email;
    private long propertiesSold;
    private long propertiesActive;
    private double revenue;
    private double commissions;
    private int rank;
}
