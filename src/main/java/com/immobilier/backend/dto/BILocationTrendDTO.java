package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BILocationTrendDTO {
    private List<String> months;
    private List<Double> monthlyRevenues;
    private List<String> durationLabels;
    private List<Long>   durationCounts;
    private List<String> agencyNames;
    private List<Double> agencyRevenues;
}
