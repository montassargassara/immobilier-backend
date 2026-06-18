package com.immobilier.backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InterestRequestDTO {
    private Long id;
    private Long propertyId;
    private String propertyTitle;
    private String propertyCity;
    private String propertyCountry;
    private String propertyMainImageUrl;
    /** VENTE or LOCATION — drives which conversion action the UI shows. */
    private String propertyCategory;
    private String fullName;
    private String email;
    private String telephone;
    private String message;
    private Double proposedBudget;
    private String status;
    private String agencyName;

    /** True once the lead has reached a terminal state (CONVERTI_* or REFUSE). */
    private Boolean locked;
    private LocalDateTime lockedAt;
    /** Message sent to the client when refused. */
    private String rejectionMessage;

    // ── Rental contract snapshot ───────────────────────────────────────────────
    private LocalDate rentalStartDate;
    private LocalDate rentalEndDate;
    private Integer rentalDurationMonths;
    private Double rentalAmount;
    private String rentalNotes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
