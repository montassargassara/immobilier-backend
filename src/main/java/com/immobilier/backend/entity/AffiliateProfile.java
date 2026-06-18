package com.immobilier.backend.entity;

import com.immobilier.backend.enums.AffiliateStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Extended profile for users with role=AFFILIATE.
 * Mirrors the ClientInfo pattern — keeps the User entity clean.
 */
@Entity
@Table(name = "affiliate_profiles", indexes = {
    @Index(name = "idx_aff_profile_user",   columnList = "user_id"),
    @Index(name = "idx_aff_profile_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AffiliateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AffiliateStatus status = AffiliateStatus.PENDING;

    // JUNIOR | MID | SENIOR | EXPERT — informational only
    @Column(name = "experience_level", length = 50)
    private String experienceLevel;

    @Column(length = 1000)
    private String notes;

    // Who approved or rejected
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) status = AffiliateStatus.PENDING;
    }
}
