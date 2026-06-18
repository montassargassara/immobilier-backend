package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BITypeBreakdownDTO {
    private String type;
    private long totalCount;
    private long soldCount;
    private long activeCount;
    private double percentage;
}
