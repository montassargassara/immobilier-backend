package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Top-performing internal staff (COMMERCIAL / RESPONSABLE_COMMERCIAL) ranking. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BIStaffRankDTO {
    private Long   id;
    private String name;
    private String email;
    private String role;
    private long   salesCount;
    private double totalCommission;
    private double pendingCommission;
    private int    rank;
}
