package com.immobilier.backend.dto;

import com.immobilier.backend.validation.PropertyStatusValidatable;
import com.immobilier.backend.validation.ValidPropertyStatus;
import lombok.Data;

@Data
@ValidPropertyStatus
public class UpdatePropertyRequest implements PropertyStatusValidatable {
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
    private String country;
    private String city;
    private Double latitude;
    private Double longitude;
    private Boolean isActive;
    private Boolean isAffiliateEligible;
    // Commission (VENTE only). Persisted from the edit form by ADMIN/SUPER_ADMIN;
    // stripped server-side for lower roles. Immutable once the property is sold/rented.
    private Double  commissionPercentage;
    private String  commissionType;
    private Integer rentalDurationMonths;

    private MediaUpdateMode mediaUpdateMode = MediaUpdateMode.REPLACE;
    private java.util.List<PropertyMediaDTO> medias;
    private java.util.List<Long> mediaIdsToDelete;
    
    public enum MediaUpdateMode {
        REPLACE, APPEND
    }
    
    /**
     * Helper method to get category based on which price is set
     */
    public String getCategory() {
        boolean hasSalePrice = prixVente != null && prixVente > 0;
        boolean hasRentalPrice = prixLocation != null && prixLocation > 0;
        
        if (hasSalePrice && !hasRentalPrice) {
            return "VENTE";
        } else if (!hasSalePrice && hasRentalPrice) {
            return "LOCATION";
        } else if (hasSalePrice && hasRentalPrice) {
            throw new IllegalArgumentException(
                "Une propriété ne peut pas être à la fois en vente et en location"
            );
        }
        return null;
    }
}