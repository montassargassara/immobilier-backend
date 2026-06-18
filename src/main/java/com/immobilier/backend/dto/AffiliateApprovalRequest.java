package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class AffiliateApprovalRequest {
    // Used for rejection or suspension only
    private String reason;
}
