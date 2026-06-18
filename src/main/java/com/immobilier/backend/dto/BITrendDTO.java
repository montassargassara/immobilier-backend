package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BITrendDTO {
    private List<String> months;
    private List<Long> salesCounts;
    private List<Long> rentalCounts;
    private List<Double> revenues;
    private List<Double> commissions;
    private List<Long> newClients;
}
