package com.immobilier.backend.entity;

import com.immobilier.backend.enums.PendingSaleApprovalStatus;
import com.immobilier.backend.enums.PropertyValidationStatus;
import com.immobilier.backend.enums.RoleType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Entity
@Table(name = "properties", indexes = {
    @Index(name = "idx_properties_is_active", columnList = "is_active"),
    @Index(name = "idx_properties_statut", columnList = "statut"),
    @Index(name = "idx_properties_type", columnList = "type"),
    @Index(name = "idx_properties_created_at", columnList = "created_at"),
    @Index(name = "idx_properties_price_vente", columnList = "prix_vente"),
    @Index(name = "idx_properties_price_location", columnList = "prix_location"),
    @Index(name = "idx_properties_commission", columnList = "commission_percentage"),
    @Index(name = "idx_properties_country", columnList = "country"),
    @Index(name = "idx_properties_city", columnList = "city"),
    @Index(name = "idx_properties_owner_type", columnList = "owner_type"),
    @Index(name = "idx_properties_agency_admin", columnList = "agency_admin_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String titre;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(name = "prix_vente")
    private Double prixVente;

    @Column(name = "prix_location")
    private Double prixLocation;

    @Column(nullable = false, length = 50)
    private String statut;

    private Double surface;

    @Column(name = "nb_chambres")
    private Integer nbChambres;

    @Column(name = "nb_salles_de_bain")
    private Integer nbSallesDeBain;

    @Column(name = "garage")
    private Boolean garage = false;

    @Column(name = "piscine")
    private Boolean piscine = false;

    @Column(name = "jardin")
    private Boolean jardin = false;

    @Column(name = "meuble")
    private Boolean meuble = false;

    @Column(name = "etage")
    private Integer etage;

    @Column(name = "parking_spaces")
    private Integer parkingSpaces;

    @Column(name = "annee_construction")
    private Integer anneeConstruction;

    @Column(name = "proche_plage")
    private Boolean prochePlage = false;

    @Column(name = "proche_transport")
    private Boolean procheTransport = false;

    @Column(name = "securite")
    private Boolean securite = false;

    @Column(name = "climatisation")
    private Boolean climatisation = false;

    @Column(nullable = false, length = 500)
    private String adresse;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String region;

    private Double latitude;
    private Double longitude;

    @Column(name = "main_image_id")
    private Long mainImageId;
    
    @Column(name = "main_video_id")
    private Long mainVideoId;
    
    @Column(name = "main_model_3d_id")
    private Long mainModel3dId;

    @Column(name = "commission_percentage")
    private Double commissionPercentage;

    @Column(name = "commission_type")
    private String commissionType = "PERCENTAGE";

    @Column(name = "base_price_for_commission")
    private Double basePriceForCommission;

    // ─── Multi-tenant ownership ───────────────────────────────────────────────
    // SUPER_ADMIN_OWNED: created by super admin, private until shared
    // AGENCY_OWNED: created by an agency (admin/staff)
    // NULL: legacy row, treated as visible to all authenticated users
    @Column(name = "owner_type", length = 30)
    private String ownerType;

    // The ADMIN user who owns this property (null for SUPER_ADMIN_OWNED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_admin_id")
    private User agencyAdmin;

    // The buyer linked by a direct sale (VENDU) or rental (LOUE) workflow.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    private User buyer;
    // ─────────────────────────────────────────────────────────────────────────

    // ─── Role-based authoring & validation workflow ──────────────────────────
    // The user who originally created this property (never overwritten on update).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    // Role of the creator at creation time — used to decide the initial workflow state.
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_role", length = 30)
    private RoleType ownerRole;

    // Validation state — separate from `statut` (which tracks DISPONIBLE/VENDU/LOUE…).
    // Only APPROVED properties are visible in agency lists, the public portal,
    // the affiliate workspace, and can be shared by SUPER_ADMIN.
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", length = 30)
    private PropertyValidationStatus validationStatus;

    // True once an ADMIN/SUPER_ADMIN has set commission. Lower roles cannot edit
    // the commission fields once locked. Defended both server-side and in the UI.
    @Column(name = "commission_locked")
    private Boolean commissionLocked = false;

    // True once an upstream validator has confirmed the price. Prevents COMMERCIAL
    // from changing the price after RESPONSABLE/ADMIN approval.
    @Column(name = "price_locked")
    private Boolean priceLocked = false;

    // Reason captured when validationStatus = REJECTED
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    // ─── Pending sale approval workflow ───────────────────────────────────────
    // COMMERCIAL/RESPONSABLE → ADMIN → (escalate if SUPER_ADMIN_OWNED) → SUPER_ADMIN
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_sale_approval", length = 20)
    private PendingSaleApprovalStatus pendingSaleApproval;

    @Column(name = "pending_sale_statut", length = 20)
    private String pendingSaleStatut;

    @Column(name = "pending_sale_rejection_reason", length = 500)
    private String pendingSaleRejectionReason;

    // Who submitted the current pending request (may be updated on escalation)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_sale_requested_by_id")
    private User pendingSaleRequestedBy;

    // Which role level must approve next (ADMIN first, then escalated to SUPER_ADMIN)
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_sale_approver_role", length = 30)
    private RoleType pendingSaleApproverRole;
    // ─────────────────────────────────────────────────────────────────────────

    // When true, active affiliates in the matching zone can see and submit offers on this property
    @Column(name = "is_affiliate_eligible")
    private Boolean isAffiliateEligible = false;

    // Set to true when an affiliate sale offer is accepted — hides property from other affiliates
    @Column(name = "is_reserved_by_affiliate")
    private Boolean isReservedByAffiliate = false;

    // ─── Rental lock fields ────────────────────────────────────────────────────
    // Duration of the rental in months; kept for UI input convenience.
    @Column(name = "rental_duration_months")
    private Integer rentalDurationMonths;

    // Timestamp when the LOUE status was set.
    @Column(name = "rental_start_date")
    private LocalDateTime rentalStartDate;

    // Computed end date (rentalStartDate + rentalDurationMonths); authoritative for lock checks.
    @Column(name = "rental_end_date")
    private LocalDateTime rentalEndDate;

    // True once status has been set to VENDU — property is permanently finalized.
    @Column(name = "is_finalized")
    private Boolean isFinalized = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void validateStatusAndCategory() {
        // Validate status based on category
        String category = getCategory();
        
        if (category == null) {
            if (statut == null) statut = "DISPONIBLE";
            if (isActive == null) isActive = true;
            if (isAffiliateEligible == null) isAffiliateEligible = false;
            if (isReservedByAffiliate == null) isReservedByAffiliate = false;
            if (commissionType == null) commissionType = "PERCENTAGE";
            if (commissionPercentage == null) {
                commissionPercentage = 0.0;
            }
            return;
        }
        
        if ("VENTE".equals(category)) {
            // Vente properties cannot have rental statuses
            if ("LOUE".equals(statut)) {
                throw new IllegalStateException(
                    "Une propriété en vente ne peut pas avoir le statut 'LOUE'. " +
                    "Statuts autorisés pour la vente: DISPONIBLE, EN_ATTENTE, VENDU"
                );
            }
            // Ensure price is set for sale
            if (prixVente == null || prixVente <= 0) {
                throw new IllegalStateException(
                    "Une propriété en vente doit avoir un prix de vente valide"
                );
            }
            // Clear rental price if set by mistake
            if (prixLocation != null && prixLocation > 0) {
                prixLocation = null;
            }
        } else if ("LOCATION".equals(category)) {
            // Rental properties cannot have sold status
            if ("VENDU".equals(statut)) {
                throw new IllegalStateException(
                    "Une propriété en location ne peut pas avoir le statut 'VENDU'. " +
                    "Statuts autorisés pour la location: DISPONIBLE, EN_ATTENTE, LOUE"
                );
            }
            // Ensure rental price is set
            if (prixLocation == null || prixLocation <= 0) {
                throw new IllegalStateException(
                    "Une propriété en location doit avoir un prix de location valide"
                );
            }
            // Clear sale price if set by mistake
            if (prixVente != null && prixVente > 0) {
                prixVente = null;
            }
            // Rental properties NEVER use the commission + affiliate workflow.
            // Force-reset these fields so a leaked or stale value cannot expose
            // the property to affiliates or imply a sale commission.
            commissionPercentage = 0.0;
            commissionType = "PERCENTAGE";
            basePriceForCommission = null;
            isAffiliateEligible = false;
            isReservedByAffiliate = false;
        }
        
        // Set default status if null
        if (statut == null) {
            statut = "DISPONIBLE";
        }
        
        if (isActive == null) isActive = true;
        if (isAffiliateEligible == null) isAffiliateEligible = false;
        if (commissionType == null) commissionType = "PERCENTAGE";
        // No automatic default commission. Commission must be set explicitly
        // (by the admin at sale validation, or on the property for affiliates).
        if (commissionPercentage == null) {
            commissionPercentage = 0.0;
        }
    }

    /**
     * Get the category of the property (VENTE or LOCATION)
     */
    public String getCategory() {
        boolean isSale = prixVente != null && prixVente > 0;
        boolean isRental = prixLocation != null && prixLocation > 0;
        
        if (isSale && !isRental) {
            return "VENTE";
        } else if (!isSale && isRental) {
            return "LOCATION";
        } else if (isSale && isRental) {
            // Both prices set - determine based on which is primary or throw
            throw new IllegalStateException(
                "Une propriété ne peut pas être à la fois en vente et en location. " +
                "Veuillez spécifier soit un prix de vente, soit un prix de location."
            );
        }
        return null;
    }
    
    /**
     * Check if status is valid for current category
     */
    public boolean isStatusValidForCategory() {
        String category = getCategory();
        if (category == null) return true;
        
        if ("VENTE".equals(category)) {
            return !"LOUE".equals(statut);
        } else if ("LOCATION".equals(category)) {
            return !"VENDU".equals(statut);
        }
        return true;
    }
    
    /**
     * Get allowed statuses for current category
     */
    public java.util.List<String> getAllowedStatuses() {
        String category = getCategory();
        if ("VENTE".equals(category)) {
            return java.util.Arrays.asList("DISPONIBLE", "EN_ATTENTE", "VENDU");
        } else if ("LOCATION".equals(category)) {
            return java.util.Arrays.asList("DISPONIBLE", "EN_ATTENTE", "LOUE");
        }
        return java.util.Arrays.asList("DISPONIBLE", "EN_ATTENTE", "VENDU", "LOUE");
    }

    /**
     * Get valid statuses for a given category (static version)
     */
    public static java.util.List<String> getAllowedStatusesForCategory(String category) {
        if ("VENTE".equals(category)) {
            return java.util.Arrays.asList("DISPONIBLE", "EN_ATTENTE", "VENDU");
        } else if ("LOCATION".equals(category)) {
            return java.util.Arrays.asList("DISPONIBLE", "EN_ATTENTE", "LOUE");
        }
        return java.util.Arrays.asList("DISPONIBLE", "EN_ATTENTE", "VENDU", "LOUE");
    }

    /**
     * Validate if a status is allowed for a category
     */
    public static boolean isStatusAllowedForCategory(String category, String status) {
        if ("VENTE".equals(category)) {
            return java.util.Arrays.asList("DISPONIBLE", "EN_ATTENTE", "VENDU").contains(status);
        } else if ("LOCATION".equals(category)) {
            return java.util.Arrays.asList("DISPONIBLE", "EN_ATTENTE", "LOUE").contains(status);
        }
        return java.util.Arrays.asList("DISPONIBLE", "EN_ATTENTE", "VENDU", "LOUE").contains(status);
    }

    public Double calculateCommissionAmount() {
        if (commissionPercentage == null) return 0.0;
        
        Double priceToUse = getPriceForCommission();
        if (priceToUse == null) return 0.0;
        
        if ("FIXED".equals(commissionType)) {
            return commissionPercentage;
        } else {
            return priceToUse * (commissionPercentage / 100);
        }
    }

    public Double getPriceForCommission() {
        if (basePriceForCommission != null) {
            return basePriceForCommission;
        }
        if (prixVente != null && prixVente > 0) {
            return prixVente;
        }
        if (prixLocation != null && prixLocation > 0) {
            return prixLocation * 12;
        }
        return null;
    }

    public String getCommissionDisplay() {
        if (commissionPercentage == null) return "N/A";
        
        if ("FIXED".equals(commissionType)) {
            return String.format("%.0f TND", commissionPercentage);
        } else {
            return String.format("%.1f%%", commissionPercentage);
        }
    }
}