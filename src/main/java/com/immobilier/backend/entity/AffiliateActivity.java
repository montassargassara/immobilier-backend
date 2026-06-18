package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "affiliate_activities", indexes = {
    @Index(name = "idx_activities_affiliate", columnList = "affiliate_id"),
    @Index(name = "idx_activities_date", columnList = "activity_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AffiliateActivity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affiliate_id", nullable = false)
    private User affiliate;
    
    @Column(name = "activity_type", nullable = false)
    private String activityType; // VIEW, SHARE, CONTACT, VISIT, SALE
    
    @Column(name = "property_id")
    private Long propertyId;
    
    @Column(name = "activity_date", nullable = false)
    private LocalDateTime activityDate;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON pour stocker des infos supplémentaires
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}