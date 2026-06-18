// UserService.java - Version complète avec toutes les fonctionnalités hiérarchiques
package com.immobilier.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.immobilier.backend.dto.ChangePasswordRequest;
import com.immobilier.backend.dto.CreateUserRequest;
import com.immobilier.backend.dto.UpdateUserRequest;
import com.immobilier.backend.dto.UserDTO;
import com.immobilier.backend.dto.UserTreeDTO;
import com.immobilier.backend.dto.RoleCountDTO;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.UserRepository;
import com.immobilier.backend.security.SecurityUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;
    private final AvatarUrlBuilder avatarUrlBuilder;

    // Mappage des rôles que chaque rôle peut créer
    private static final Map<RoleType, List<RoleType>> ALLOWED_CREATION = Map.of(
        RoleType.SUPER_ADMIN, List.of(RoleType.ADMIN, RoleType.CLIENT, RoleType.AFFILIATE),
        RoleType.ADMIN, List.of(RoleType.RESPONSABLE_COMMERCIAL, RoleType.CLIENT, RoleType.AFFILIATE),
        RoleType.RESPONSABLE_COMMERCIAL, List.of(RoleType.COMMERCIAL, RoleType.CLIENT),
        RoleType.COMMERCIAL, List.of(RoleType.CLIENT),
        RoleType.AFFILIATE, List.of(),
        RoleType.CLIENT, List.of()
    );

    // Hiérarchie des rôles
    private static final Map<RoleType, Integer> ROLE_LEVEL = Map.of(
        RoleType.SUPER_ADMIN, 5,
        RoleType.ADMIN, 4,
        RoleType.RESPONSABLE_COMMERCIAL, 3,
        RoleType.COMMERCIAL, 2,
        RoleType.AFFILIATE, 1,
        RoleType.CLIENT, 1
    );

    // Internal organizational hierarchy ONLY — clients (CLIENT / CLIENT_PUBLIC) and
    // AFFILIATE partners are never part of the staff org tree (they live in CRM /
    // affiliate management). The tree shows: SUPER_ADMIN → ADMIN → RESPONSABLE_COMMERCIAL → COMMERCIAL.
    private static final java.util.Set<RoleType> STAFF_HIERARCHY_ROLES = java.util.EnumSet.of(
        RoleType.SUPER_ADMIN,
        RoleType.ADMIN,
        RoleType.RESPONSABLE_COMMERCIAL,
        RoleType.COMMERCIAL
    );

    // ==================== CRUD DE BASE ====================
    
    @Transactional
    public UserDTO createUser(CreateUserRequest request) {
        log.info("Création d'un utilisateur avec rôle {}", request.getRole());
        
        // Vérifier si l'email existe déjà
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email déjà utilisé");
        }
        
        // Vérifier si le téléphone existe déjà
        if (request.getTelephone() != null && 
            userRepository.existsByTelephone(request.getTelephone())) {
            throw new RuntimeException("Téléphone déjà utilisé");
        }
        
        // Créer l'utilisateur
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setTelephone(request.getTelephone());
        user.setRole(request.getRole());
        user.setIsActive(true);
        if (request.getCommissionRate() != null) {
            user.setCommissionRate(request.getCommissionRate());
        }

        User savedUser = userRepository.save(user);
        log.info("Utilisateur créé avec succès: {}", savedUser.getEmail());
        
        return convertToDTO(savedUser);
    }
    
    @Transactional
    public UserDTO createUserWithHierarchy(CreateUserRequest request, User creator) {
        log.info("Création d'un utilisateur avec rôle {} par {}", request.getRole(), creator.getEmail());
        
        // Vérifier si l'email existe déjà
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Un utilisateur avec cet email existe déjà");
        }
        
        // Vérifier si le téléphone existe déjà
        if (request.getTelephone() != null && userRepository.existsByTelephone(request.getTelephone())) {
            throw new RuntimeException("Un utilisateur avec ce téléphone existe déjà");
        }
        
        // Vérifier si le créateur peut créer ce rôle
        if (!canCreateRole(creator, request.getRole())) {
            throw new RuntimeException(
                String.format("Un %s ne peut pas créer un utilisateur avec le rôle %s", 
                    creator.getRole(), request.getRole())
            );
        }
        
        // Créer l'utilisateur
        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setNom(request.getNom());
        newUser.setPrenom(request.getPrenom());
        newUser.setTelephone(request.getTelephone());
        newUser.setRole(request.getRole());
        newUser.setIsActive(true);
        newUser.setParent(creator); // Lier au créateur
        
        User savedUser = userRepository.save(newUser);
        log.info("Utilisateur créé avec succès: {} (ID: {})", savedUser.getEmail(), savedUser.getId());
        
        return convertToDTO(savedUser);
    }
    
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return convertToDTO(user);
    }
    
    public User getUserEntityById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
    
    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        return convertToDTO(user);
    }
    
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<UserDTO> getUsersByRole(RoleType role) {
        return userRepository.findByRole(role)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    public List<UserDTO> getActiveUsers() {
        return userRepository.findByIsActiveTrue()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public UserDTO updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        if (request.getNom() != null) {
            user.setNom(request.getNom());
        }
        if (request.getPrenom() != null) {
            user.setPrenom(request.getPrenom());
        }
        if (request.getTelephone() != null) {
            // Vérifier si le téléphone n'est pas utilisé par un autre utilisateur
            if (!request.getTelephone().equals(user.getTelephone()) &&
                userRepository.existsByTelephone(request.getTelephone())) {
                throw new RuntimeException("Téléphone déjà utilisé");
            }
            user.setTelephone(request.getTelephone());
        }
        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }
        if (request.getCommissionRate() != null) {
            user.setCommissionRate(request.getCommissionRate());
        }

        User updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }
    
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        // Vérifier l'ancien mot de passe
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Mot de passe actuel incorrect");
        }
        
        // Mettre à jour le mot de passe
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
    
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        
        // Ne pas supprimer physiquement, désactiver
        user.setIsActive(false);
        userRepository.save(user);
    }
    
    // ==================== MÉTHODES HIÉRARCHIQUES ====================
    
    /**
     * Vérifie si un utilisateur peut créer un utilisateur avec le rôle spécifié
     */
    public boolean canCreateRole(User creator, RoleType roleToCreate) {
        if (creator == null || !creator.getIsActive()) {
            return false;
        }
        
        List<RoleType> allowedRoles = ALLOWED_CREATION.get(creator.getRole());
        return allowedRoles != null && allowedRoles.contains(roleToCreate);
    }
    
    /**
     * Récupère les rôles que l'utilisateur peut créer
     */
    public List<RoleType> getCreatableRoles(User user) {
        if (user == null || !user.getIsActive()) {
            return new ArrayList<>();
        }
        
        List<RoleType> allowedRoles = ALLOWED_CREATION.get(user.getRole());
        return allowedRoles != null ? new ArrayList<>(allowedRoles) : new ArrayList<>();
    }

    /**
     * Vérifie si un utilisateur peut voir un autre utilisateur (basé sur hiérarchie)
     */
    public boolean canViewUser(User viewer, User target) {
        if (viewer == null || target == null) {
            return false;
        }
        
        // SUPER_ADMIN voit tout le monde
        if (viewer.getRole() == RoleType.SUPER_ADMIN) {
            return true;
        }
        
        // Un utilisateur peut voir lui-même
        if (viewer.getId().equals(target.getId())) {
            return true;
        }
        
        // Vérifier si target est un descendant de viewer
        return isDescendant(viewer.getId(), target.getId());
    }

    /**
     * Récupère tous les utilisateurs qu'un utilisateur peut voir
     */
    public List<User> getViewableUsers(User viewer) {
        if (viewer.getRole() == RoleType.SUPER_ADMIN) {
            return userRepository.findAll();
        }
        
        // Récupérer tous les descendants
        List<User> descendants = userRepository.findAllDescendants(viewer.getId());
        
        // Ajouter l'utilisateur lui-même
        descendants.add(0, viewer);
        
        return descendants;
    }
    
    /**
     * Récupère les DTO des utilisateurs visibles
     */
    public List<UserDTO> getViewableUserDTOs(User viewer) {
        return getViewableUsers(viewer).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Récupère les utilisateurs paginés qu'un utilisateur peut voir
     */
    public Page<User> getViewableUsersPaginated(User viewer, Pageable pageable) {
        if (viewer.getRole() == RoleType.SUPER_ADMIN) {
            return userRepository.findAll(pageable);
        }
        
        // Pour les utilisateurs non SUPER_ADMIN, on récupère les descendants
        return userRepository.findByParentId(viewer.getId(), pageable);
    }

    /**
     * Récupère les utilisateurs qu'un utilisateur peut gérer (ses enfants directs)
     */
    public List<UserDTO> getDirectChildren(User parent) {
        List<User> children = userRepository.findByParentId(parent.getId());
        return children.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Récupère les enfants par rôle
     */
    public List<UserDTO> getChildrenByRole(User parent, RoleType role) {
        List<User> children = userRepository.findByParentId(parent.getId());
        return children.stream()
            .filter(u -> u.getRole() == role)
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Récupère la hiérarchie complète sous forme d'arbre
     */
/**
 * Récupère la hiérarchie complète sous forme d'arbre
 */
public UserTreeDTO getUserTree(User root) {
    UserTreeDTO tree = new UserTreeDTO();
    tree.setUser(convertToDTO(root));
    
    // Only internal staff roles belong in the org tree — exclude CLIENT,
    // CLIENT_PUBLIC and AFFILIATE so they never leak into the staff hierarchy.
    List<User> children = userRepository.findByParentId(root.getId()).stream()
        .filter(u -> u.getRole() != null && STAFF_HIERARCHY_ROLES.contains(u.getRole()))
        .collect(Collectors.toList());
    List<UserTreeDTO> childTrees = children.stream()
        .map(this::getUserTree)
        .collect(Collectors.toList());
    tree.setChildren(childTrees);
    
    // Calculer le nombre de descendants
    int descendantCount = childTrees.stream()
        .mapToInt(c -> 1 + c.getDescendantCount())
        .sum();
    tree.setDescendantCount(descendantCount);
    
    // Compter les rôles des descendants
    Map<RoleType, Long> roleCountMap = new HashMap<>();
    for (UserTreeDTO child : childTrees) {
        RoleType childRole = child.getUser().getRole();
        roleCountMap.put(childRole, roleCountMap.getOrDefault(childRole, 0L) + 1);
        
        // Ajouter les comptes des enfants
        if (child.getRoleCounts() != null) {
            for (RoleCountDTO rc : child.getRoleCounts()) {
                roleCountMap.put(rc.getRole(), 
                    roleCountMap.getOrDefault(rc.getRole(), 0L) + rc.getCount());
            }
        }
    }
    
    // CORRECTION ICI - Utilisation de setters au lieu du constructeur
    List<RoleCountDTO> roleCounts = roleCountMap.entrySet().stream()
        .map(e -> {
            RoleCountDTO rc = new RoleCountDTO();
            rc.setRole(e.getKey());
            rc.setCount(e.getValue());
            return rc;
        })
        .collect(Collectors.toList());
    tree.setRoleCounts(roleCounts);
    
    return tree;
}

    /**
     * Récupère l'arbre hiérarchique complet pour SUPER_ADMIN
     */
    public List<UserTreeDTO> getFullHierarchy() {
        // Récupérer tous les SUPER_ADMIN (racines)
        List<User> roots = userRepository.findByRole(RoleType.SUPER_ADMIN);
        return roots.stream()
            .map(this::getUserTree)
            .collect(Collectors.toList());
    }

    /**
     * Récupère la hiérarchie pour un utilisateur spécifique (lui et ses descendants)
     */
    public UserTreeDTO getUserHierarchy(Long userId) {
        User user = getUserEntityById(userId);
        return getUserTree(user);
    }

    /**
     * Vérifie si un utilisateur peut partager un client avec un autre utilisateur
     */
    public boolean canShareClient(User owner, User target) {
        // Le propriétaire ne peut partager qu'avec des utilisateurs en dessous dans la hiérarchie
        return isDescendant(owner.getId(), target.getId());
    }

    /**
     * Récupère tous les utilisateurs disponibles pour le partage de clients
     */
    public List<UserDTO> getAvailableUsersForSharing(User owner) {
        List<User> descendants = userRepository.findAllDescendants(owner.getId());
        
        // Filtrer pour ne garder que les commerciaux et responsables
        return descendants.stream()
            .filter(u -> u.getRole() == RoleType.COMMERCIAL || 
                        u.getRole() == RoleType.RESPONSABLE_COMMERCIAL ||
                        u.getRole() == RoleType.ADMIN)
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    // ==================== MÉTHODES STATISTIQUES ====================
    
    public long countUsersByRole(RoleType role) {
        return userRepository.countByRole(role);
    }
    
    public long countAllUsers() {
        return userRepository.count();
    }
    
    public Map<RoleType, Long> getUsersCountByRole() {
        List<Object[]> results = userRepository.countByRoleGrouped();
        Map<RoleType, Long> countMap = new HashMap<>();
        for (Object[] result : results) {
            RoleType role = (RoleType) result[0];
            Long count = (Long) result[1];
            countMap.put(role, count);
        }
        return countMap;
    }
    
    // ==================== MÉTHODES DE CONVERSION ====================
    
    public UserDTO convertToDTO(User user) {
        if (user == null) return null;
        
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setNom(user.getNom());
        dto.setPrenom(user.getPrenom());
        dto.setTelephone(user.getTelephone());
        dto.setRole(user.getRole());
        dto.setIsActive(user.getIsActive());
        dto.setCommissionRate(user.getCommissionRate());
        dto.setAvatarUrl(avatarUrlBuilder.build(user.getProfileImagePath()));
        dto.setCreatedAt(user.getCreatedAt());
        
        // Ajouter le parent (créateur)
        if (user.getParent() != null) {
            dto.setParentId(user.getParent().getId());
            dto.setParentName(user.getParent().getFullName());
        }
        
        // Ajouter le nombre d'enfants
        dto.setChildrenCount(user.getChildren().size());
        
        return dto;
    }

        // Dans UserService.java, ajoutez cette méthode publique
    public boolean isDescendant(Long ancestorId, Long descendantId) {
        List<User> descendants = userRepository.findAllDescendants(ancestorId);
        return descendants.stream().anyMatch(u -> u.getId().equals(descendantId));
    }
}