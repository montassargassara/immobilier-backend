package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "zone_payment_requests")
@Data
@NoArgsConstructor
public class ZonePaymentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affiliate_id", nullable = false)
    private User affiliate;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(nullable = false, length = 100)
    private String city;

    /** Display name for the zone (city + country). */
    @Column(nullable = false, length = 200)
    private String zoneName;

    @Column(nullable = false)
    private Double amount;

    @Column(name = "is_premium")
    private Boolean isPremium = false;

    /** Relative path under uploads/payments/ — used to serve the proof image. */
    @Column(name = "proof_image_path", length = 300)
    private String proofImagePath;

    /** PENDING | APPROVED | REJECTED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
