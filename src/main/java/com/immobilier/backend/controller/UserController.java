// UserController.java - Version modifiée sans dépendance à SecurityUtils
package com.immobilier.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.security.CustomUserDetails;
import com.immobilier.backend.service.AffiliateService;
import com.immobilier.backend.service.UserService;

import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    private final AffiliateService affiliateService;
    
    // Helper method to get current user
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) principal;
            return userService.getUserEntityById(userDetails.getUserId());
        }
        
        throw new RuntimeException("Impossible de récupérer l'utilisateur courant");
    }
    
    // ==================== DASHBOARD ====================
    
    // Dans UserController.java, assurez-vous que cet endpoint existe
    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userService.countAllUsers());
        stats.put("usersByRole", userService.getUsersCountByRole());
        stats.put("activeUsers", userService.getActiveUsers().size());
        
        // Ajouter les stats clients et affiliés
        stats.put("clientCount", userService.countUsersByRole(RoleType.CLIENT));
        stats.put("affiliateCount", userService.countUsersByRole(RoleType.AFFILIATE));
        
        return ResponseEntity.ok(stats);
    }
    
    // ==================== GESTION UTILISATEURS CRUD ====================
    
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserDTO> createUser(@RequestBody CreateUserRequest request) {
        UserDTO createdUser = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }
    
    @PostMapping("/with-hierarchy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDTO> createUserWithHierarchy(@RequestBody CreateUserRequest request) {
        User currentUser = getCurrentUser();
        UserDTO createdUser = userService.createUserWithHierarchy(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }
    
    @PostMapping("/admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserDTO> createAdmin(@RequestBody CreateUserRequest request) {
        request.setRole(RoleType.ADMIN);
        UserDTO createdAdmin = userService.createUser(request);
        return ResponseEntity.ok(createdAdmin);
    }
    
    @PostMapping("/responsable")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserDTO> createResponsable(@RequestBody CreateUserRequest request) {
        request.setRole(RoleType.RESPONSABLE_COMMERCIAL);
        UserDTO createdResponsable = userService.createUser(request);
        return ResponseEntity.ok(createdResponsable);
    }
    
    @PostMapping("/affiliate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AffiliateProfileDTO> createAffiliate(@Valid @RequestBody CreateAffiliateRequest request) {
        log.info("👑 Creating affiliate: {}", request.getEmail());
        AffiliateProfileDTO createdAffiliate = affiliateService.registerAffiliate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAffiliate);
    }
    
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        User targetUser = userService.getUserEntityById(id);
        
        if (!userService.canViewUser(currentUser, targetUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }
    
    @GetMapping("/role/{role}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<List<UserDTO>> getUsersByRole(@PathVariable RoleType role) {
        List<UserDTO> users = userService.getUsersByRole(role);
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<List<UserDTO>> getActiveUsers() {
        List<UserDTO> users = userService.getActiveUsers();
        return ResponseEntity.ok(users);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request) {
        UserDTO updatedUser = userService.updateUser(id, request);
        return ResponseEntity.ok(updatedUser);
    }
    
    @PutMapping("/{id}/password")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<String> changePassword(
            @PathVariable Long id,
            @RequestBody ChangePasswordRequest request) {
        userService.changePassword(id, request);
        return ResponseEntity.ok("Mot de passe modifié avec succès");
    }
    
    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<UserDTO> activateUser(@PathVariable Long id) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setIsActive(true);
        UserDTO updatedUser = userService.updateUser(id, request);
        return ResponseEntity.ok(updatedUser);
    }
    
    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<UserDTO> deactivateUser(@PathVariable Long id) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setIsActive(false);
        UserDTO updatedUser = userService.updateUser(id, request);
        return ResponseEntity.ok(updatedUser);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("Utilisateur désactivé avec succès");
    }
    
    // ==================== GESTION DES AFFILIATES ====================
    
    @GetMapping("/affiliates")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllAffiliates() {
        log.info("👑 Getting all affiliates");
        List<UserDTO> affiliates = userService.getUsersByRole(RoleType.AFFILIATE);
        return ResponseEntity.ok(affiliates);
    }
    
    @GetMapping("/affiliates/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AffiliateStatsDTO> getAffiliateDetails(@PathVariable Long id) {
        log.info("👑 Getting affiliate details for ID: {}", id);
        AffiliateStatsDTO stats = affiliateService.getStats(id);
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/affiliates/transactions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<AffiliateTransactionDTO>> getAllAffiliateTransactions() {
        log.info("👑 Getting all affiliate transactions");
        List<AffiliateTransactionDTO> transactions = affiliateService.getAllTransactions();
        return ResponseEntity.ok(transactions);
    }
    
    @GetMapping("/affiliates/ranking")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'RESPONSABLE_COMMERCIAL')")
    public ResponseEntity<List<AffiliateStatsDTO>> getAffiliateRankingForAdmin() {
        log.info("👑 Getting affiliate ranking for admin");
        List<AffiliateStatsDTO> ranking = affiliateService.getMonthlyRanking();
        return ResponseEntity.ok(ranking);
    }
    
    @PutMapping("/affiliates/{affiliateId}/regions/{regionId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AffiliateRegionDTO> updateAffiliateRegion(
            @PathVariable Long affiliateId,
            @PathVariable Long regionId,
            @Valid @RequestBody RegionSelection regionSelection) {
        log.info("👑 Updating region {} for affiliate {}", regionId, affiliateId);
        AffiliateRegionDTO updatedRegion = affiliateService.updateRegion(affiliateId, regionId, regionSelection);
        return ResponseEntity.ok(updatedRegion);
    }
    
    // ==================== HIÉRARCHIE ET ARBRES ====================
    
    @GetMapping("/full-tree")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<UserTreeDTO>> getFullHierarchy() {
        List<UserTreeDTO> hierarchy = userService.getFullHierarchy();
        return ResponseEntity.ok(hierarchy);
    }
    
    @GetMapping("/my-tree")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserTreeDTO> getMyHierarchy() {
        User currentUser = getCurrentUser();
        UserTreeDTO hierarchy = userService.getUserHierarchy(currentUser.getId());
        return ResponseEntity.ok(hierarchy);
    }
    
    @GetMapping("/tree/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<UserTreeDTO> getUserHierarchy(@PathVariable Long userId) {
        User currentUser = getCurrentUser();
        User targetUser = userService.getUserEntityById(userId);
        
        if (!userService.canViewUser(currentUser, targetUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        UserTreeDTO hierarchy = userService.getUserHierarchy(userId);
        return ResponseEntity.ok(hierarchy);
    }
    
    @GetMapping("/my-children")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserDTO>> getMyChildren() {
        User currentUser = getCurrentUser();
        List<UserDTO> children = userService.getDirectChildren(currentUser);
        return ResponseEntity.ok(children);
    }
    
    @GetMapping("/children/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<List<UserDTO>> getUserChildren(@PathVariable Long userId) {
        User currentUser = getCurrentUser();
        User targetUser = userService.getUserEntityById(userId);
        
        if (!userService.canViewUser(currentUser, targetUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<UserDTO> children = userService.getDirectChildren(targetUser);
        return ResponseEntity.ok(children);
    }
    
    @GetMapping("/viewable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserDTO>> getViewableUsers() {
        User currentUser = getCurrentUser();
        List<UserDTO> viewableUsers = userService.getViewableUserDTOs(currentUser);
        return ResponseEntity.ok(viewableUsers);
    }
    
    @GetMapping("/viewable/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<UserDTO>> getViewableUsersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        User currentUser = getCurrentUser();
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<User> userPage = userService.getViewableUsersPaginated(currentUser, pageable);
        Page<UserDTO> dtoPage = userPage.map(userService::convertToDTO);
        
        return ResponseEntity.ok(dtoPage);
    }
    
    @GetMapping("/available-for-sharing")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserDTO>> getAvailableForSharing() {
        User currentUser = getCurrentUser();
        List<UserDTO> availableUsers = userService.getAvailableUsersForSharing(currentUser);
        return ResponseEntity.ok(availableUsers);
    }
    
    @GetMapping("/can-create/{role}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Boolean>> canCreateRole(@PathVariable RoleType role) {
        User currentUser = getCurrentUser();
        boolean canCreate = userService.canCreateRole(currentUser, role);
        return ResponseEntity.ok(Map.of("canCreate", canCreate));
    }
    
    @GetMapping("/creatable-roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RoleType>> getCreatableRoles() {
        User currentUser = getCurrentUser();
        List<RoleType> creatableRoles = userService.getCreatableRoles(currentUser);
        return ResponseEntity.ok(creatableRoles);
    }
    
    // ==================== INITIALISATION ====================
    
    @PostMapping("/init")
    public ResponseEntity<String> initSuperAdmin() {
        List<UserDTO> superAdmins = userService.getUsersByRole(RoleType.SUPER_ADMIN);
        if (superAdmins.isEmpty()) {
            CreateUserRequest request = new CreateUserRequest();
            request.setEmail("superadmin@immobilier.com");
            request.setPassword("admin123");
            request.setNom("Super");
            request.setPrenom("Admin");
            request.setTelephone("+21612345678");
            request.setRole(RoleType.SUPER_ADMIN);
            userService.createUser(request);
            return ResponseEntity.ok("Super Admin créé avec succès");
        }
        return ResponseEntity.ok("Super Admin existe déjà");
    }

    // Ajoutez ces méthodes dans UserController.java

/**
 * Récupère les utilisateurs par plusieurs rôles
 */
@GetMapping("/by-roles")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public ResponseEntity<List<UserDTO>> getUsersByRoles(@RequestParam String roles) {
    List<String> roleNames = Arrays.asList(roles.split(","));
    List<RoleType> roleTypes = roleNames.stream()
        .map(roleName -> {
            if ("CLIENT".equals(roleName)) return RoleType.CLIENT;
            if ("AFFILIATE_CLIENT".equals(roleName)) return RoleType.AFFILIATE;
            if ("AFFILIATE".equals(roleName)) return RoleType.AFFILIATE;
            return RoleType.valueOf(roleName);
        })
        .collect(Collectors.toList());
    
    List<UserDTO> allUsers = new ArrayList<>();
    for (RoleType roleType : roleTypes) {
        allUsers.addAll(userService.getUsersByRole(roleType));
    }
    
    // Remove duplicates
    List<UserDTO> distinctUsers = allUsers.stream()
        .collect(Collectors.toMap(UserDTO::getId, u -> u, (u1, u2) -> u1))
        .values()
        .stream()
        .collect(Collectors.toList());
    
    return ResponseEntity.ok(distinctUsers);
}
}