package com.immobilier.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PublicPropertyCardDTO {
    private Long id;
    private String titre;
    private String type;
    private String category;
    private Double prixVente;
    private Double prixLocation;
    private Double surface;
    private Integer nbChambres;
    private Integer nbSallesDeBain;
    private Boolean garage;
    private Boolean piscine;
    private Boolean jardin;
    private Boolean meuble;
    private Integer etage;
    private Integer parkingSpaces;
    private Boolean climatisation;
    private Boolean securite;
    private String city;
    private String country;
    private String region;
    private Double latitude;
    private Double longitude;
    private String mainImageUrl;
    private boolean hasModel3d;
    // Kept for backward compat with any legacy callers
    private String agencyName;
    private PublicAgencyDTO agency;
    private LocalDateTime createdAt;
}
