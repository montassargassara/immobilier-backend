package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Persisted relation between an affiliate and the real buyer they brought,
 * for a completed sale/rental.
 *
 * The buyer is a CRM lead ONLY — never a {@link User}, never logs in, never
 * appears in the staff hierarchy. This entity carries the buyer's contact
 * details and links affiliate ↔ buyer ↔ property ↔ sale offer ↔ transaction.
 *
 * Created in {@code SaleOfferService.completeOffer()} alongside the
 * {@link AffiliateTransaction}, idempotently (one row per SaleOffer).
 */
@Entity
@Table(name = "affiliate_customer_relations", indexes = {
        @Index(name = "idx_acr_affiliate", columnList = "affiliate_id"),
        @Index(name = "idx_acr_property", columnList = "property_id"),
        @Index(name = "idx_acr_offer", columnList = "sale_offer_id"),
        @Index(name = "idx_acr_transaction", columnList = "transaction_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AffiliateCustomerRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affiliate_id", nullable = false)
    private User affiliate;

    // ── Buyer (CRM lead — NO User account) ────────────────────────────────
    @Column(name = "buyer_name", nullable = false)
    private String buyerName;

    @Column(name = "buyer_email")
    private String buyerEmail;

    @Column(name = "buyer_phone")
    private String buyerPhone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_offer_id")
    private SaleOffer saleOffer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private AffiliateTransaction affiliateTransaction;

    @Column(name = "transaction_type", length = 20)
    private String transactionType; // SALE | RENT

    @Column(name = "property_price")
    private Double propertyPrice;

    @Column(name = "commission_amount")
    private Double commissionAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
