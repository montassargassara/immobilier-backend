package com.immobilier.backend.entity;

import com.immobilier.backend.enums.SaleOfferStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a sale offer submitted by an affiliate on behalf of a buyer.
 *
 * Workflow: PENDING → ACCEPTED → COMPLETED  (happy path)
 *           PENDING → REJECTED              (owner declines)
 *           PENDING → CANCELLED             (affiliate withdraws)
 *
 * When status moves to ACCEPTED an AffiliateTransaction is created separately.
 */
@Entity
@Table(name = "sale_offers", indexes = {
    @Index(name = "idx_offer_affiliate",  columnList = "affiliate_id"),
    @Index(name = "idx_offer_property",   columnList = "property_id"),
    @Index(name = "idx_offer_status",     columnList = "status"),
    @Index(name = "idx_offer_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaleOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affiliate_id", nullable = false)
    private User affiliate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleOfferStatus status = SaleOfferStatus.PENDING;

    // ── Buyer info supplied by the affiliate ────────────────────────────────
    @Column(name = "buyer_name", nullable = false, length = 200)
    private String buyerName;

    @Column(name = "buyer_email", nullable = false, length = 200)
    private String buyerEmail;

    @Column(name = "buyer_phone", length = 50)
    private String buyerPhone;

    // Optional price the buyer is proposing (may differ from listing price)
    @Column(name = "offered_price")
    private Double offeredPrice;

    @Column(length = 1000)
    private String message;

    // ── Owner response ────────────────────────────────────────────────────────
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by")
    private User respondedBy;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    // ── Commission snapshot (populated when ACCEPTED) ─────────────────────
    @Column(name = "commission_percentage")
    private Double commissionPercentage;

    @Column(name = "commission_amount")
    private Double commissionAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) status = SaleOfferStatus.PENDING;
    }
}
