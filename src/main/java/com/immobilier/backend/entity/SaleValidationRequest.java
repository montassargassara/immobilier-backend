package com.immobilier.backend.entity;

import com.immobilier.backend.enums.PendingSaleApprovalStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Stores a pending cross-ownership sale/rental request.
 * Created when a user tries to sell/rent a property they don't own,
 * so the real owner can approve or reject before the status changes.
 */
@Entity
@Table(name = "sale_validation_requests")
@Data
@NoArgsConstructor
public class SaleValidationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Property involved ─────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    // ── Who requested the sale ────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    // ── Buyer info (existing user if present) ─────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    private User buyer;

    // New-buyer fields (used when buyer is null / creating on-the-fly)
    @Column(length = 120)
    private String clientNom;

    @Column(length = 120)
    private String clientPrenom;

    @Column(length = 200)
    private String clientEmail;

    @Column(length = 60)
    private String clientTelephone;

    // ── Transaction details ───────────────────────────────────────────────────

    /** VENDU or LOUE */
    @Column(nullable = false, length = 20)
    private String targetStatus;

    /** DIRECT_SALE or CRM_LEAD */
    @Column(length = 20)
    private String source;

    // CRM lead reference (if source = CRM_LEAD)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_request_id")
    private InterestRequest interestRequest;

    // Rental contract fields (relevant when targetStatus = LOUE)
    @Column(length = 20)
    private String rentalStartDate;       // ISO date string e.g. "2026-06-01"

    private Integer rentalDurationMonths;

    private Double rentalAmount;

    @Column(length = 500)
    private String rentalNotes;

    // ── Admin-entered terms at approval time (mandatory, no default) ──────────
    // The reviewing ADMIN/SUPER_ADMIN must enter the final price and the
    // commission % when approving. Stored for audit on the request itself.

    private Double finalPrice;

    private Double commissionPercentage;

    // ── Workflow state ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PendingSaleApprovalStatus status;

    @Column(length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    public void prePersist() {
        if (status == null) status = PendingSaleApprovalStatus.PENDING;
    }
}
