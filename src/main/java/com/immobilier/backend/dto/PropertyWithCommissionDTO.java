package com.immobilier.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PropertyWithCommissionDTO {
    private Long id;
    private String titre;
    private String description;
    private String type;
    private Double prixVente;
    private Double prixLocation;
    private String statut;
    private Double surface;
    private Integer nbChambres;
    private String adresse;
    private String country;  // Add this field
    private String city;     // Add this field
    private String region;
    private Double latitude;
    private Double longitude;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Commission information
    private Double commissionPercentage;
    private String commissionType;
    private Double commissionAmount;
    private String commissionDisplay;
    private Double basePriceForCommission;
    private String priceForCommissionDisplay;
    
    // Media info
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
}