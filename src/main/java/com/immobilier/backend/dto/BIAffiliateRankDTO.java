package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BIAffiliateRankDTO {
    private long id;
    private String name;
    private String email;
    private long salesCompleted;
    private double totalCommissions;
    private int rank;
}
