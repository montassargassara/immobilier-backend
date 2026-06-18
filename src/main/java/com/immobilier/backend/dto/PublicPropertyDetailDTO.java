package com.immobilier.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PublicPropertyDetailDTO {
    private Long id;
    private String titre;
    private String description;
    private String type;
    private String category;
    private String statut;
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
    private Integer anneeConstruction;
    private Boolean prochePlage;
    private Boolean procheTransport;
    private Boolean securite;
    private Boolean climatisation;
    private String adresse;
    private String city;
    private String country;
    private String region;
    private Double latitude;
    private Double longitude;
    private String mainImageUrl;
    private List<String> imageUrls;
    private boolean hasModel3d;
    private String model3dUrl;
    private String model3dFormat;
    private boolean hasVideo;
    private String mainVideoUrl;
    private List<String> videoUrls;
    // Kept for backward compat
    private Long agencyAdminId;
    private String agencyName;
    private PublicAgencyDTO agency;
    private LocalDateTime createdAt;
}
