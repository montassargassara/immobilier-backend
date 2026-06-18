package com.immobilier.backend.enums;

public enum ShareRequestStatus {
    PENDING,    // awaiting agency response
    ACCEPTED,   // agency accepted → PropertySharedAgency created
    REJECTED,   // agency declined
    CANCELLED   // super admin cancelled before response
}
