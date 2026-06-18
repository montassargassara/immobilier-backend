package com.immobilier.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MonthlyBonusDTO {
    private Long id;
    private Long affiliateId;
    private String affiliateName;
    private Integer rankingMonth;
    private Integer rankingYear;
    private Integer rank;
    private Double rewardAmount;
    private Boolean isPaid;
    private LocalDateTime createdAt;
}
