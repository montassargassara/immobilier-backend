package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BITopCityDTO {
    private String city;
    private String country;
    private long soldCount;
    private long activeCount;
    private double totalRevenue;
}
