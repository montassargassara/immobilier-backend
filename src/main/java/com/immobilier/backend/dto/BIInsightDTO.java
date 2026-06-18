package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BIInsightDTO {
    private String type;    // "success" | "warning" | "danger" | "info"
    private String icon;
    private String title;
    private String message;
}
