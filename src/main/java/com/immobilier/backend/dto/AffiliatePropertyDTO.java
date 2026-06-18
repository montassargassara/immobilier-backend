package com.immobilier.backend.dto;

import lombok.Data;

/**
 * Lightweight property view for affiliates.
 * Contains only fields the affiliate is allowed to see.
 */
@Data
public class AffiliatePropertyDTO {
    private Long id;
    private String titre;
    private String type;
    private String statut;
    private Double prixVente;
    private Double prixLocation;
    private Double surface;
    private Integer nbChambres;
    private String adresse;
    private String city;
    private String region;
    private Double latitude;
    private Double longitude;

    // Commission info the affiliate needs
    private Double commissionPercentage;
    private String commissionType;
    private Double commissionAmount;

    private String mainImageUrl;
    private boolean hasMainImage;

    private boolean isReservedByAffiliate;
}
