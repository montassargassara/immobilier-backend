package com.immobilier.backend.dto;

import com.immobilier.backend.enums.AffiliateStatus;
import lombok.Data;

@Data
public class AgencyAffiliateDTO {
    private Long affiliateId;
    private String prenom;
    private String nom;
    private String email;
    private String telephone;
    private AffiliateStatus status;
    private int clientsApportes;
    private int biensVendus;
    private double totalCommissions;
}
