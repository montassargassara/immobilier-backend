package com.immobilier.backend.enums;

public enum AffiliateStatus {
    PENDING,    // Application submitted, awaiting Super Admin approval
    ACTIVE,     // Approved and operational
    REJECTED,   // Application rejected by Super Admin
    SUSPENDED   // Temporarily disabled by Super Admin
}
