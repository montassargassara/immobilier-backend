package com.immobilier.backend.entity;

import com.immobilier.backend.enums.ShareRequestStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "property_share_requests", indexes = {
    @Index(name = "idx_psr_agency_admin", columnList = "agency_admin_id"),
    @Index(name = "idx_psr_property",     columnList = "property_id"),
    @Index(name = "idx_psr_status",       columnList = "status"),
    @Index(name = "idx_psr_shared_by",    columnList = "shared_by_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertyShareRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The property being shared
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    // The ADMIN user receiving the share request
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_admin_id", nullable = false)
    private User agencyAdmin;

    // The SUPER_ADMIN who initiated the request
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by_id", nullable = false)
    private User sharedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShareRequestStatus status = ShareRequestStatus.PENDING;

    // Commission agreed for this specific share (overrides property default)
    @Column(name = "commission_percentage")
    private Double commissionPercentage = 0.0;

    @Column(name = "commission_type", length = 20)
    private String commissionType = "PERCENTAGE"; // PERCENTAGE | FIXED

    // Optional message from super admin
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    // Optional reason from agency on rejection
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
