package com.immobilier.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PropertyListDTO {
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
    
    // ✅ Seulement les métadonnées, pas les BLOBs !
    private String mainImageName;
    private String mainImageType;
    private Long mainImageSize;
    private String mainImageUrl;
    private boolean hasMainImage;
    
    private String model3dName;
    private String model3dType;
    private Long model3dSize;
    private String model3dUrl;
    private boolean hasModel3d;
    
    // Ownership / multi-tenant fields
    private String ownerType;
    private Long agencyAdminId;
    private String agencyAdminName;

    private Boolean isAffiliateEligible;

    // Validation workflow
    private String validationStatus;
    private String ownerRole;
    private Long createdById;
    private String createdByName;
    private Boolean commissionLocked;
    private Boolean priceLocked;

    // Rental lock / finalized fields
    private java.time.LocalDateTime rentalStartDate;
    private java.time.LocalDateTime rentalEndDate;
    private Integer rentalDurationMonths;
    private Boolean isFinalized;
    private Boolean isStatusLocked;
    private String statusLockReason;

    // Pending sale approval workflow
    private String pendingSaleApproval;        // PENDING | APPROVED | REJECTED | null
    private String pendingSaleStatut;          // VENDU or LOUE — the requested status
    private String pendingSaleRejectionReason;
    private Long pendingSaleRequestedById;
    private String pendingSaleRequestedByName;
    private String pendingSaleApproverRole;    // ADMIN | SUPER_ADMIN — who must approve next

    // CRM: number of interest requests for this property
    private Integer interestCount;

    // True when property is EN_ATTENTE due to a cross-ownership validation request (SaleValidationRequest PENDING)
    private boolean hasPendingValidation;

    // Direct sale / rental buyer link
    private Long   buyerId;
    private String buyerName;
    private String buyerEmail;
    private String buyerTelephone;

    // Affiliate-originated sale (buyer is a CRM lead from AffiliateCustomerRelation,
    // NEVER a User). Populated only for terminal (VENDU/LOUE) affiliate sales.
    private boolean viaAffiliate;
    private String  affiliateName;
    private Double  affiliateCommissionAmount;
    private Double  affiliateCommissionPercentage;
    private String  affiliateCommissionType;   // PERCENTAGE | FIXED
    private Boolean affiliateCommissionPaid;

    // ✅ Pour les listes, pas de médias (trop lourd)
    // private List<PropertyMediaDTO> medias;
}