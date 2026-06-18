package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class AuthDTO {
    private String token;
    private String email;
    private String role;
    private String nom;
    private String prenom;
    private Long userId;
    private String avatarUrl;

    public AuthDTO(String token, String email, String role,
                       String nom, String prenom, Long userId, String avatarUrl) {
        this.token = token;
        this.email = email;
        this.role = role;
        this.nom = nom;
        this.prenom = prenom;
        this.userId = userId;
        this.avatarUrl = avatarUrl;
    }

    // Backward-compatible: callers with no avatar (e.g. public client portal)
    public AuthDTO(String token, String email, String role,
                       String nom, String prenom, Long userId) {
        this(token, email, role, nom, prenom, userId, null);
    }
}