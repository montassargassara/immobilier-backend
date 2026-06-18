// UserDTO amélioré
package com.immobilier.backend.dto;

import lombok.Data;
import com.immobilier.backend.enums.RoleType;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserDTO {
    private Long id;
    private String email;
    private String nom;
    private String prenom;
    private String telephone;
    private RoleType role;
    private Boolean isActive;
    private Double commissionRate;
    private String avatarUrl;
    private LocalDateTime createdAt;
    
    // Champs hiérarchiques
    private Long parentId;
    private String parentName;
    private Integer childrenCount;
    private List<RoleCountDTO> childrenByRole;
}