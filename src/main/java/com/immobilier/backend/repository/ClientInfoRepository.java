package com.immobilier.backend.repository;

import com.immobilier.backend.entity.ClientInfo;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientInfoRepository extends JpaRepository<ClientInfo, Long> {
    
    // ========== MÉTHODES DE BASE ==========

    Optional<ClientInfo> findByUser(User user);

    /** Safe single-result variant — returns the first row when multiple exist (affiliates). */
    Optional<ClientInfo> findFirstByUser(User user);

    Optional<ClientInfo> findByUserId(Long userId);

    /** Check existence of a CRM lead for a specific (user, agency) pair. */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ClientInfo c " +
           "WHERE c.user.id = :userId AND c.agencyAdminId = :agencyAdminId")
    boolean existsByUserIdAndAgencyAdminId(@Param("userId") Long userId,
                                           @Param("agencyAdminId") Long agencyAdminId);

    /** Returns ALL ClientInfo rows for a user (one per agency + optional PRIVATE row). */
    @Query("SELECT c FROM ClientInfo c WHERE c.user.id = :userId")
    java.util.List<ClientInfo> findAllByUserId(@Param("userId") Long userId);
    
    // ========== MÉTHODES POUR SUPER_ADMIN (VISION GLOBALE) ==========
    
    @Query("SELECT c FROM ClientInfo c WHERE c.user.role IN :roles")
    Page<ClientInfo> findByUserRoleIn(@Param("roles") List<RoleType> roles, Pageable pageable);
    
    @Query("SELECT c FROM ClientInfo c WHERE c.user.role IN :roles")
    List<ClientInfo> findByUserRoleIn(@Param("roles") List<RoleType> roles);
    
    // ⚠️ CORRECTION: Cette méthode était mal nommée
    @Query("SELECT c FROM ClientInfo c WHERE c.user.role = :role AND c.user.isActive = true")
    long countActiveByUserRole(@Param("role") RoleType role);
    
    // ========== MÉTHODES POUR AGENCE (VISION FILTRÉE) ==========
    
    @Query("SELECT c FROM ClientInfo c WHERE c.agencyAdminId = :agencyAdminId")
    Page<ClientInfo> findByAgencyAdminId(@Param("agencyAdminId") Long agencyAdminId, Pageable pageable);
    
    @Query("SELECT c FROM ClientInfo c WHERE c.agencyAdminId = :agencyAdminId AND c.user.role IN :roles")
    Page<ClientInfo> findByAgencyAdminIdAndRoles(@Param("agencyAdminId") Long agencyAdminId,
                                                  @Param("roles") List<RoleType> roles,
                                                  Pageable pageable);
    
    // ========== RECHERCHE ==========
    
    @Query("SELECT c FROM ClientInfo c WHERE c.agencyAdminId = :agencyAdminId AND " +
           "(LOWER(c.user.nom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.user.prenom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.user.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "c.user.telephone LIKE CONCAT('%', :search, '%'))")
    Page<ClientInfo> searchByKeywordForAgency(@Param("agencyAdminId") Long agencyAdminId,
                                              @Param("search") String search,
                                              Pageable pageable);
    
    @Query("SELECT c FROM ClientInfo c WHERE " +
           "LOWER(c.user.nom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.user.prenom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.user.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "c.user.telephone LIKE CONCAT('%', :search, '%')")
    Page<ClientInfo> searchByKeywordForSuperAdmin(@Param("search") String search, Pageable pageable);
    
    @Query("SELECT c FROM ClientInfo c WHERE c.user.role IN :roles AND " +
           "(LOWER(c.user.nom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.user.prenom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.user.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "c.user.telephone LIKE CONCAT('%', :search, '%'))")
    Page<ClientInfo> searchByKeyword(@Param("roles") List<RoleType> roles, 
                                     @Param("search") String search, 
                                     Pageable pageable);
    
    // ========== FILTRES PAR COMMERCIAL ==========
    
    @Query("SELECT c FROM ClientInfo c WHERE c.user.role IN :roles AND c.commercial.id = :commercialId")
    Page<ClientInfo> findByCommercial(@Param("roles") List<RoleType> roles,
                                      @Param("commercialId") Long commercialId,
                                      Pageable pageable);
    
    @Query("SELECT c FROM ClientInfo c WHERE c.agencyAdminId = :agencyAdminId AND c.commercial.id = :commercialId")
    Page<ClientInfo> findByAgencyAndCommercial(@Param("agencyAdminId") Long agencyAdminId,
                                                @Param("commercialId") Long commercialId,
                                                Pageable pageable);
    
    // ========== FILTRES PAR BUDGET ==========
    
    @Query("SELECT c FROM ClientInfo c WHERE c.user.role IN :roles AND c.budgetEstime BETWEEN :minBudget AND :maxBudget")
    Page<ClientInfo> findByBudgetRange(@Param("roles") List<RoleType> roles,
                                       @Param("minBudget") Double minBudget,
                                       @Param("maxBudget") Double maxBudget,
                                       Pageable pageable);
    
    @Query("SELECT c FROM ClientInfo c WHERE c.agencyAdminId = :agencyAdminId AND c.budgetEstime BETWEEN :minBudget AND :maxBudget")
    Page<ClientInfo> findByAgencyAndBudgetRange(@Param("agencyAdminId") Long agencyAdminId,
                                                 @Param("minBudget") Double minBudget,
                                                 @Param("maxBudget") Double maxBudget,
                                                 Pageable pageable);
    
    // ========== FILTRES PAR ACHETEURS ==========
    
    @Query("SELECT c FROM ClientInfo c WHERE c.user.role IN :roles AND c.nombreAchats > 0")
    Page<ClientInfo> findBuyers(@Param("roles") List<RoleType> roles, Pageable pageable);
    
    @Query("SELECT c FROM ClientInfo c WHERE c.agencyAdminId = :agencyAdminId AND c.nombreAchats > 0")
    Page<ClientInfo> findBuyersByAgency(@Param("agencyAdminId") Long agencyAdminId, Pageable pageable);
    
    // ========== VÉRIFICATIONS ==========
    
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ClientInfo c " +
           "WHERE c.id = :clientId AND c.agencyAdminId = :agencyAdminId")
    boolean existsByClientIdAndAgencyAdminId(@Param("clientId") Long clientId, 
                                              @Param("agencyAdminId") Long agencyAdminId);
    
    // ========== STATISTIQUES ==========
    
    // Stats par agence
    long countByAgencyAdminId(Long agencyAdminId);
    
    @Query("SELECT COUNT(c) FROM ClientInfo c WHERE c.agencyAdminId = :agencyAdminId AND c.user.isActive = true")
    long countActiveByAgencyAdminId(@Param("agencyAdminId") Long agencyAdminId);
    
    @Query("SELECT COUNT(c) FROM ClientInfo c WHERE c.agencyAdminId = :agencyAdminId AND c.user.role = :role")
    long countByAgencyAdminIdAndRole(@Param("agencyAdminId") Long agencyAdminId, @Param("role") RoleType role);
    
    // Stats globales
    @Query("SELECT COUNT(c) FROM ClientInfo c")
    long countAllClients();
    
    long countByUserRole(RoleType role);
    
    // ⚠️ CORRECTION: Ajout de cette méthode manquante
    @Query("SELECT COUNT(c) FROM ClientInfo c WHERE c.user.role = :role")
    long countByRole(@Param("role") RoleType role);
    
    // Stats de ventes
    @Query("SELECT COALESCE(SUM(c.totalAchats), 0) FROM ClientInfo c WHERE " +
           "(:agencyAdminId IS NULL OR c.agencyAdminId = :agencyAdminId) AND " +
           "c.user.role = 'CLIENT' AND FUNCTION('MONTH', c.updatedAt) = :month AND FUNCTION('YEAR', c.updatedAt) = :year")
    Double sumVentesByMonth(@Param("month") int month, 
                            @Param("year") int year, 
                            @Param("agencyAdminId") Long agencyAdminId);
    
    @Query("SELECT COALESCE(SUM(c.commissionGeneree), 0) FROM ClientInfo c WHERE " +
           "(:agencyAdminId IS NULL OR c.agencyAdminId = :agencyAdminId) AND " +
           "c.user.role = 'AFFILIATE' AND FUNCTION('MONTH', c.updatedAt) = :month AND FUNCTION('YEAR', c.updatedAt) = :year")
    Double sumCommissionAffiliatesByMonth(@Param("month") int month, 
                                          @Param("year") int year,
                                          @Param("agencyAdminId") Long agencyAdminId);
    
    // Stats par commercial
    @Query("SELECT COUNT(c) FROM ClientInfo c WHERE c.commercial.id = :commercialId")
    long countByCommercialId(@Param("commercialId") Long commercialId);
    
    @Query("SELECT COALESCE(SUM(c.totalAchats), 0) FROM ClientInfo c WHERE c.commercial.id = :commercialId")
    Double sumVentesByCommercial(@Param("commercialId") Long commercialId);
    
    // ⚠️ CORRECTION: Ajout de la méthode countActiveByUserRole (était mal définie)
    @Query("SELECT COUNT(c) FROM ClientInfo c WHERE c.user.role = :role AND c.user.isActive = true")
    long countActiveByUserRoleSingle(@Param("role") RoleType role);

    // Dans ClientInfoRepository.java, ajoutez ces méthodes

// ========== MÉTHODES DE VISIBILITÉ ==========

// Pour SUPER_ADMIN: tous les clients
@Query("SELECT c FROM ClientInfo c")
Page<ClientInfo> findAllForSuperAdmin(Pageable pageable);

// Pour ADMIN: clients agence + clients privés partagés
@Query("SELECT DISTINCT c FROM ClientInfo c " +
       "LEFT JOIN ClientSharedAgency csa ON c.id = csa.client.id " +
       "WHERE c.visibilityType = 'AGENCY_CLIENT' AND c.agencyAdminId = :adminId " +
       "OR c.visibilityType = 'PRIVATE_CLIENT' AND csa.admin.id = :adminId")
Page<ClientInfo> findVisibleClientsForAdmin(@Param("adminId") Long adminId, Pageable pageable);

// Pour les enfants (COMMERCIAL, etc.): clients de leur agence
@Query("SELECT c FROM ClientInfo c WHERE c.visibilityType = 'AGENCY_CLIENT' AND c.agencyAdminId = :agencyAdminId")
Page<ClientInfo> findAgencyClientsByAgencyAdminId(@Param("agencyAdminId") Long agencyAdminId, Pageable pageable);

// Récupérer les agences avec lesquelles un client privé est partagé
@Query("SELECT csa.admin.id FROM ClientSharedAgency csa WHERE csa.client.id = :clientId")
List<Long> findSharedAgencyIdsByClientId(@Param("clientId") Long clientId);

// Vérifier si un client privé est partagé avec une agence
@Query("SELECT CASE WHEN COUNT(csa) > 0 THEN true ELSE false END FROM ClientSharedAgency csa " +
       "WHERE csa.client.id = :clientId AND csa.admin.id = :adminId")
boolean isSharedWithAgency(@Param("clientId") Long clientId, @Param("adminId") Long adminId);

// Ajoutez ces méthodes dans ClientInfoRepository.java

// Pour les clients récents (SUPER_ADMIN)
@Query("SELECT c FROM ClientInfo c ORDER BY c.createdAt DESC")
List<ClientInfo> findTop6ByOrderByCreatedAtDesc(Pageable pageable);

default List<ClientInfo> findTop6ByOrderByCreatedAtDesc() {
    return findTop6ByOrderByCreatedAtDesc(PageRequest.of(0, 6));
}

// Pour les clients d'une agence (enfants)
@Query("SELECT c FROM ClientInfo c WHERE c.agencyAdminId = :agencyAdminId ORDER BY c.createdAt DESC")
List<ClientInfo> findTop6ByAgencyAdminIdOrderByCreatedAtDesc(@Param("agencyAdminId") Long agencyAdminId, Pageable pageable);

default List<ClientInfo> findTop6ByAgencyAdminIdOrderByCreatedAtDesc(Long agencyAdminId) {
    return findTop6ByAgencyAdminIdOrderByCreatedAtDesc(agencyAdminId, PageRequest.of(0, 6));
}

// Pour ADMIN - clients agence + partagés
@Query("SELECT c FROM ClientInfo c WHERE c.agencyAdminId = :adminId OR c.id IN :clientIds ORDER BY c.createdAt DESC")
List<ClientInfo> findTop6ByAgencyAdminIdOrIdInOrderByCreatedAtDesc(
    @Param("adminId") Long adminId, 
    @Param("clientIds") List<Long> clientIds,
    Pageable pageable);

default List<ClientInfo> findTop6ByAgencyAdminIdOrIdInOrderByCreatedAtDesc(Long adminId, List<Long> clientIds) {
    if (clientIds == null || clientIds.isEmpty()) {
        return findTop6ByAgencyAdminIdOrderByCreatedAtDesc(adminId);
    }
    return findTop6ByAgencyAdminIdOrIdInOrderByCreatedAtDesc(adminId, clientIds, PageRequest.of(0, 6));
}

}