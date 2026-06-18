package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "interest_requests", indexes = {
        @Index(name = "idx_interest_user", columnList = "user_id"),
        @Index(name = "idx_interest_property", columnList = "property_id"),
        @Index(name = "idx_interest_owner", columnList = "owner_user_id"),
        @Index(name = "idx_interest_status", columnList = "status"),
        @Index(name = "idx_interest_locked", columnList = "locked")
})
@Data
@NoArgsConstructor
public class InterestRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    /** Property owner at time of submission — agency admin or super admin. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User ownerUser;

    @Column(name = "full_name", length = 160)
    private String fullName;

    @Column(length = 160)
    private String email;

    @Column(length = 60)
    private String telephone;

    @Column(length = 1000)
    private String message;

    @Column(name = "proposed_budget")
    private Double proposedBudget;

    // Expanded to 50 chars to fit VISITE_PROGRAMMEE, CONVERTI_LOCATION, etc.
    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    // Locked once the lead reaches a terminal state (CONVERTI_* or REFUSE)
    @Column(nullable = false)
    private Boolean locked = false;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    // Reason/message sent to the client on REFUSE
    @Column(name = "rejection_message", length = 500)
    private String rejectionMessage;

    // ── Rental contract (populated when status → CONVERTI_LOCATION) ──────────
    @Column(name = "rental_start_date")
    private LocalDate rentalStartDate;

    @Column(name = "rental_end_date")
    private LocalDate rentalEndDate;

    @Column(name = "rental_duration_months")
    private Integer rentalDurationMonths;

    @Column(name = "rental_amount")
    private Double rentalAmount;

    @Column(name = "rental_notes", length = 1000)
    private String rentalNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
