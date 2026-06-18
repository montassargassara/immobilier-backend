package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "property_shared_agencies", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"property_id", "agency_admin_id"})
})
@Data
public class PropertySharedAgency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    // The ADMIN user this property is shared with
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_admin_id", nullable = false)
    private User agencyAdmin;

    // The SUPER_ADMIN who performed the sharing
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by_id", nullable = false)
    private User sharedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
