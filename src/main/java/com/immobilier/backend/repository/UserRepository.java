// UserRepository.java
package com.immobilier.backend.repository;

import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    List<User> findByRole(RoleType role);
    
    Page<User> findByRole(RoleType role, Pageable pageable);
    
    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByTelephone(String telephone);

    boolean existsByTelephoneAndIdNot(String telephone, Long id);
    
    List<User> findByIsActiveTrue();
    
    List<User> findByRoleAndIsActiveTrue(RoleType role);
    
    // Récupérer les utilisateurs créés par un parent spécifique
    List<User> findByParentId(Long parentId);
    
    Page<User> findByParentId(Long parentId, Pageable pageable);
    
    // Récupérer tous les descendants d'un utilisateur (récursif)
    @Query(value = """
        WITH RECURSIVE user_tree AS (
            SELECT * FROM users WHERE id = :userId
            UNION ALL
            SELECT u.* FROM users u
            INNER JOIN user_tree ut ON u.parent_id = ut.id
        )
        SELECT * FROM user_tree WHERE id != :userId
        """, nativeQuery = true)
    List<User> findAllDescendants(@Param("userId") Long userId);
    
    // Compter les enfants par rôle
    @Query("SELECT COUNT(u) FROM User u WHERE u.parent.id = :parentId AND u.role = :role")
    long countChildrenByRole(@Param("parentId") Long parentId, @Param("role") RoleType role);
    
    // Vérifier si un utilisateur peut créer un certain rôle
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.id = :parentId AND u.role IN :allowedRoles")
    boolean canCreateRole(@Param("parentId") Long parentId, @Param("allowedRoles") List<RoleType> allowedRoles);
    
    // Statistiques
    long countByRole(RoleType role);
    
    long countByIsActive(boolean isActive);
    
    @Query("SELECT u.role, COUNT(u) FROM User u GROUP BY u.role")
    List<Object[]> countByRoleGrouped();

    // Ajoutez ces méthodes à UserRepository.java existant

// Récupérer l'ADMIN parent (remonter la hiérarchie)
@Query(value = """
    WITH RECURSIVE user_parents AS (
        SELECT * FROM users WHERE id = :userId
        UNION ALL
        SELECT u.* FROM users u
        INNER JOIN user_parents up ON u.id = up.parent_id
    )
    SELECT * FROM user_parents WHERE role = 'ADMIN' LIMIT 1
    """, nativeQuery = true)
Optional<User> findTopAdminAncestor(@Param("userId") Long userId);

// Vérifier si un utilisateur est sous un ADMIN
@Query(value = """
    WITH RECURSIVE user_tree AS (
        SELECT * FROM users WHERE id = :userId
        UNION ALL
        SELECT u.* FROM users u
        INNER JOIN user_tree ut ON u.id = ut.parent_id
    )
    SELECT COUNT(*) > 0 FROM user_tree WHERE role = 'ADMIN' AND id = :adminId
    """, nativeQuery = true)
boolean isUnderAdmin(@Param("userId") Long userId, @Param("adminId") Long adminId);

    // ── BI growth analytics ──────────────────────────────────────────────────
    @Query("SELECT MONTH(u.createdAt), YEAR(u.createdAt), COUNT(u) FROM User u " +
           "WHERE u.role = :role AND u.createdAt >= :since " +
           "GROUP BY YEAR(u.createdAt), MONTH(u.createdAt) ORDER BY YEAR(u.createdAt), MONTH(u.createdAt)")
    List<Object[]> countNewUsersByRoleByMonth(@Param("role") com.immobilier.backend.enums.RoleType role,
                                              @Param("since") java.time.LocalDateTime since);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.createdAt >= :start AND u.createdAt < :end")
    long countNewUsersByRoleBetween(@Param("role") com.immobilier.backend.enums.RoleType role,
                                    @Param("start") java.time.LocalDateTime start,
                                    @Param("end") java.time.LocalDateTime end);
}