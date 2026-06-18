package com.immobilier.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentClientsDTO {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String role;
    private LocalDateTime createdAt;
    private String visibilityType;
    private Long agencyAdminId;
}