package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.immobilier.backend.enums.RoleType;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_email", columnList = "email"),
    @Index(name = "idx_users_role", columnList = "role"),
    @Index(name = "idx_users_parent_id", columnList = "parent_id"),
    @Index(name = "idx_users_is_active", columnList = "is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String nom;
    
    @Column(nullable = false)
    private String prenom;
    
    @Column(unique = true)
    private String telephone;

    @Column(name = "profile_image_path")
    private String profileImagePath;

    // VARCHAR (not MySQL ENUM) so adding a new RoleType value doesn't require
    // a destructive schema change. Migration for existing DBs:
    //   ALTER TABLE users MODIFY COLUMN role VARCHAR(40) NOT NULL;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(40)")
    private RoleType role;
    
    @Column(name = "is_active")
    private Boolean isActive = true;

    // Commission rate (%) earned by internal staff (COMMERCIAL / RESPONSABLE_COMMERCIAL)
    // when a sale or rental they brokered is completed. 0 = no staff commission.
    @Column(name = "commission_rate")
    private Double commissionRate = 0.0;

    // Hiérarchie - parent utilisateur
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private User parent;
    
    // Enfants créés par cet utilisateur
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<User> children = new ArrayList<>();
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public String getFullName() {
        return prenom + " " + nom;
    }
    
    public boolean isActive() {
        return isActive != null && isActive;
    }
    
    public boolean hasRole(RoleType roleType) {
        return this.role != null && this.role.equals(roleType);
    }
    
    public boolean isAdmin() {
        return hasRole(RoleType.ADMIN) || hasRole(RoleType.SUPER_ADMIN);
    }
    
    // Méthode toString
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", nom='" + nom + '\'' +
                ", prenom='" + prenom + '\'' +
                ", role=" + role +
                ", isActive=" + isActive +
                '}';
    }
    
    // Méthodes equals et hashCode (pour les collections)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        User user = (User) o;
        
        if (id != null ? !id.equals(user.id) : user.id != null) return false;
        return email != null ? email.equals(user.email) : user.email == null;
    }
    
    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (email != null ? email.hashCode() : 0);
        return result;
    }
    
    // Constructeur pour le builder (package-private pour être utilisé par Builder)
    User(Long id, String email, String password, String nom, String prenom, 
         String telephone, RoleType role, Boolean isActive, 
         LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.nom = nom;
        this.prenom = prenom;
        this.telephone = telephone;
        this.role = role;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.children = new ArrayList<>();
    }
    
    // Builder pattern
    public static class Builder {
        private Long id;
        private String email;
        private String password;
        private String nom;
        private String prenom;
        private String telephone;
        private RoleType role;
        private Boolean isActive = true;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private User parent;
        
        public Builder id(Long id) {
            this.id = id;
            return this;
        }
        
        public Builder email(String email) {
            this.email = email;
            return this;
        }
        
        public Builder password(String password) {
            this.password = password;
            return this;
        }
        
        public Builder nom(String nom) {
            this.nom = nom;
            return this;
        }
        
        public Builder prenom(String prenom) {
            this.prenom = prenom;
            return this;
        }
        
        public Builder telephone(String telephone) {
            this.telephone = telephone;
            return this;
        }
        
        public Builder role(RoleType role) {
            this.role = role;
            return this;
        }
        
        public Builder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }
        
        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public Builder parent(User parent) {
            this.parent = parent;
            return this;
        }
        
        public User build() {
            User user = new User(id, email, password, nom, prenom, telephone, 
                                role, isActive, createdAt, updatedAt);
            user.setParent(parent);
            return user;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}