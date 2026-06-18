package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Unified commission record for internal staff and agencies.
 *
 * Affiliate commissions are NOT stored here — they remain in
 * {@link AffiliateTransaction} so the existing affiliate workflow,
 * rankings and BI are not disturbed.
 *
 * beneficiaryType:
 *  - "STAFF"  → a COMMERCIAL / RESPONSABLE_COMMERCIAL who brokered the deal
 *  - "AGENCY" → an agency that directly sold a SUPER_ADMIN_OWNED shared property
 *
 * All enum-like columns are VARCHAR (never MySQL ENUM) per project migration rules.
 */
@Entity
@Table(name = "commissions", indexes = {
        @Index(name = "idx_commission_beneficiary", columnList = "beneficiary_id"),
        @Index(name = "idx_commission_property", columnList = "property_id"),
        @Index(name = "idx_commission_type", columnList = "beneficiary_type"),
        @Index(name = "idx_commission_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Commission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private User beneficiary;

    @Column(name = "beneficiary_type", nullable = false, length = 20)
    private String beneficiaryType; // STAFF | AGENCY

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "transaction_type", length = 20)
    private String transactionType; // SALE | RENT

    @Column(name = "property_price", nullable = false)
    private Double propertyPrice;

    @Column(name = "commission_type", length = 20)
    private String commissionType; // PERCENTAGE | FIXED

    @Column(name = "commission_rate", nullable = false)
    private Double commissionRate;

    @Column(name = "commission_amount", nullable = false)
    private Double commissionAmount;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING | PAID

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = "PENDING";
        if (commissionType == null) commissionType = "PERCENTAGE";
    }
}
