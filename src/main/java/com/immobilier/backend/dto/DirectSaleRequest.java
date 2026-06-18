package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class DirectSaleRequest {

    /** VENDU or LOUE */
    private String targetStatus;

    // ── Buyer identity ───────────────────────────────────────────────────────

    /** ID of an existing CLIENT / CLIENT_PUBLIC user to link as buyer. Null → create new. */
    private Long existingClientId;

    /** Used when creating a new buyer account */
    private String clientNom;
    private String clientPrenom;
    private String clientEmail;
    private String clientTelephone;

    // ── Rental contract fields (required when targetStatus = LOUE) ───────────

    private String  rentalStartDate;       // ISO date string: "2026-06-01"
    private Integer rentalDurationMonths;
    private Double  rentalAmount;
    private String  rentalNotes;
}
