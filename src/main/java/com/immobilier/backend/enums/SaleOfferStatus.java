package com.immobilier.backend.enums;

public enum SaleOfferStatus {
    PENDING,    // Submitted by affiliate, awaiting owner decision
    ACCEPTED,   // Owner accepted — sale process started
    REJECTED,   // Owner declined — with optional reason
    COMPLETED,  // Sale finalised — commission recorded
    CANCELLED   // Cancelled by the affiliate before a response
}
