package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class ClientPublicProfileDTO {
    private Long id;
    private String email;
    private String nom;
    private String prenom;
    private String telephone;
    private String role;

    public ClientPublicProfileDTO() {}

    public ClientPublicProfileDTO(Long id, String email, String nom, String prenom,
                                  String telephone, String role) {
        this.id = id;
        this.email = email;
        this.nom = nom;
        this.prenom = prenom;
        this.telephone = telephone;
        this.role = role;
    }
}
