package com.immobilier.backend.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Payload for converting a CRM lead to CONVERTI_VENTE or CONVERTI_LOCATION.
 * Rental contract fields are mandatory when targetStatus = CONVERTI_LOCATION.
 */
@Data
public class ConvertLeadRequest {

    /** CONVERTI_VENTE or CONVERTI_LOCATION */
    private String targetStatus;

    // ── Rental contract (required for CONVERTI_LOCATION) ─────────────────────
    private LocalDate rentalStartDate;
    private Integer rentalDurationMonths;
    private Double rentalAmount;
    private String rentalNotes;

    // ── Refusal (required for REFUSE) ─────────────────────────────────────────
    /** Optional message communicated to the public client when refusing. */
    private String rejectionMessage;
}
