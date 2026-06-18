package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class UpdateCommissionRequest {
    private Double commissionPercentage;
    private String commissionType;
    private Double basePriceForCommission;
}