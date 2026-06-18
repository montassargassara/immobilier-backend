package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "affiliate_regions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AffiliateRegion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affiliate_id", nullable = false)
    private User affiliate;
    
    @Column(nullable = false, length = 100)
    private String regionName;

    /** Country part of the affiliate's zone (e.g. "Tunisia"). */
    @Column(length = 100)
    private String country;

    /** City part of the affiliate's zone (e.g. "Tunis"). */
    @Column(length = 100)
    private String city;

    @Column(length = 255)
    private String regionDescription;

    @Column(name = "is_active")
    private Boolean isActive = true;

    /** Whether this zone required a payment (false for the first free zone). */
    @Column(name = "is_paid")
    private Boolean isPaid = false;

    /** Amount paid in TND (50 or 100). Null for the free zone. */
    @Column(name = "price_paid")
    private Double pricePaid;

    /** Premium zones command 100 TND vs. 50 TND for standard paid zones. */
    @Column(name = "is_premium")
    private Boolean isPremium = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (isActive == null)  isActive  = true;
        if (isPaid == null)    isPaid    = false;
        if (isPremium == null) isPremium = false;

        // Backward-compat: if country/city not provided but regionName follows
        // the legacy "Country,City" format, split it. Otherwise treat regionName
        // as a city-only zone (the country side stays null and skips strict matching).
        if ((country == null || country.isBlank()) && (city == null || city.isBlank())
                && regionName != null && regionName.contains(",")) {
            String[] parts = regionName.split(",", 2);
            country = parts[0].trim();
            city = parts[1].trim();
        }

        if (country != null) country = country.trim();
        if (city != null) city = city.trim();
        if (regionName != null) regionName = regionName.trim();
    }
}