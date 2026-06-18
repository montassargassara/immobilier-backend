package com.immobilier.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PropertyDTO {
    private Long id;
    private String titre;
    private String description;
    private String type;
    private Double prixVente;
    private Double prixLocation;
    private String statut;
    private Double surface;
    private Integer nbChambres;
    private Integer nbSallesDeBain;
    private Boolean garage;
    private Boolean piscine;
    private Boolean jardin;
    private Boolean meuble;
    private Integer etage;
    private Integer parkingSpaces;
    private Integer anneeConstruction;
    private Boolean prochePlage;
    private Boolean procheTransport;
    private Boolean securite;
    private Boolean climatisation;
    private String adresse;
    private String country;  // Add this field
    private String city;     // Add this field
    private Double latitude;
    private Double longitude;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Commission fields
    private Double commissionPercentage;
    private String commissionType;

    // Ownership / multi-tenant fields
    private String ownerType;         // SUPER_ADMIN_OWNED | AGENCY_OWNED | null (legacy)
    private Long agencyAdminId;
    private String agencyAdminName;
    private java.util.List<Long> sharedWithAgencyIds;

    private Boolean isAffiliateEligible;

    // ─── Validation workflow ─────────────────────────────────────────────────
    private String validationStatus;     // PENDING_RESPONSABLE | PENDING_ADMIN | APPROVED | REJECTED
    private String ownerRole;            // role of the creator
    private Long createdById;
    private String createdByName;
    private Boolean commissionLocked;
    private Boolean priceLocked;
    private String rejectionReason;

    // ─── Pending sale approval workflow ──────────────────────────────────────
    private String pendingSaleApproval;        // PENDING | APPROVED | REJECTED | null
    private String pendingSaleStatut;          // VENDU or LOUE — the requested status
    private String pendingSaleRejectionReason;
    private Long pendingSaleRequestedById;
    private String pendingSaleRequestedByName;
    private String pendingSaleApproverRole;    // ADMIN | SUPER_ADMIN — who must approve next
    
    // ─── Rental lock fields ────────────────────────────────────────────────────
    private Integer rentalDurationMonths;
    private LocalDateTime rentalStartDate;
    private LocalDateTime rentalEndDate;
    private Boolean isFinalized;
    // True when manual status changes are blocked (VENDU, locked LOUE, EN_ATTENTE via affiliate)
    private Boolean isStatusLocked;
    private String statusLockReason;

    // Image fields
    private String mainImageName;
    private String mainImageType;
    private Long mainImageSize;
    private String mainImageUrl;
    private boolean hasMainImage;
    
    // Model 3D fields
    private String model3dName;
    private String model3dType;
    private Long model3dSize;
    private String model3dUrl;
    private boolean hasModel3d;
    
    // Media list
    private List<PropertyMediaDTO> medias;
}