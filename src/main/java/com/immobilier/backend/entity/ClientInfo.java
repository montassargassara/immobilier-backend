package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_infos", indexes = {
    @Index(name = "idx_client_visibility", columnList = "visibility_type"),
    @Index(name = "idx_client_agency_admin", columnList = "agency_admin_id"),
    @Index(name = "idx_client_created_by", columnList = "created_by_id")
})
@Data
public class ClientInfo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "budget_estime")
    private Double budgetEstime;
    
    @Column(name = "zone_recherchee", length = 255)
    private String zoneRecherchee;
    
    @ManyToOne
    @JoinColumn(name = "commercial_id")
    private User commercial;
    
    @Column(name = "code_affiliation", unique = true)
    private String codeAffiliation;
    
    @Column(name = "taux_commission")
    private Double tauxCommission;
    
    @Column(length = 255)
    private String source;
    
    @Column(name = "nombre_achats")
    private Integer nombreAchats = 0;

    @Column(name = "nombre_locations")
    private Integer nombreLocations = 0;

    @Column(name = "nombre_reservations")
    private Integer nombreReservations = 0;

    @Column(name = "total_achats")
    private Double totalAchats = 0.0;
    
    @Column(name = "commission_generee")
    private Double commissionGeneree = 0.0;
    
    @Column(name = "nombre_ventes_liees")
    private Integer nombreVentesLiees = 0;
    
    // ========== NOUVEAUX CHAMPS DE VISIBILITÉ ==========
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;
    
    @Column(name = "visibility_type", nullable = false)
    private String visibilityType; // "PRIVATE_CLIENT" or "AGENCY_CLIENT"
    
    @Column(name = "agency_admin_id")
    private Long agencyAdminId; // Nullable for PRIVATE_CLIENT
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}