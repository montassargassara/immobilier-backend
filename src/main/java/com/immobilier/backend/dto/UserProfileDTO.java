package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class UserProfileDTO {
    private Long id;
    private String prenom;
    private String nom;
    private String email;
    private String telephone;
    private String role;
    private String avatarUrl;
}
