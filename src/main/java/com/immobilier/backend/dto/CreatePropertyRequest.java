package com.immobilier.backend.dto;

import com.immobilier.backend.validation.PropertyStatusValidatable;
import com.immobilier.backend.validation.ValidPropertyStatus;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@ValidPropertyStatus
public class CreatePropertyRequest implements PropertyStatusValidatable {
    @NotBlank(message = "Le titre est obligatoire")
    private String titre;
    
    private String description;
    
    @NotBlank(message = "Le type est obligatoire")
    private String type;
    
    // Either prixVente OR prixLocation must be set — enforced cross-field by
    // PropertyService.validateCategoryAndPrices() and Property.@PrePersist.
    // Do NOT add @NotNull here: LOCATION properties legitimately leave prixVente null.
    private Double prixVente;

    private Double prixLocation;
    
    private String statut = "DISPONIBLE";
    
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
    
    @NotBlank(message = "L'adresse est obligatoire")
    private String adresse;
    
    private String country;
    private String city;
    private Double latitude;
    private Double longitude;

    // Commission is optional — null/0 means no commission
    private Double commissionPercentage;
    private String commissionType = "PERCENTAGE";
    private Double basePriceForCommission;

    // SUPER_ADMIN only: directly assign a new property to a specific agency admin
    private Long agencyAdminId;

    // Whether active affiliates in matching zones can see and submit offers on this property
    private Boolean isAffiliateEligible = false;

    private List<PropertyMediaDTO> medias;
    
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