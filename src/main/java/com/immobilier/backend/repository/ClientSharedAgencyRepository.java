package com.immobilier.backend.repository;

import com.immobilier.backend.entity.ClientSharedAgency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ClientSharedAgencyRepository extends JpaRepository<ClientSharedAgency, Long> {
    
    List<ClientSharedAgency> findByClientId(Long clientId);
    
    List<ClientSharedAgency> findByAdminId(Long adminId);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM ClientSharedAgency csa WHERE csa.client.id = :clientId AND csa.admin.id = :adminId")
    void deleteByClientIdAndAdminId(@Param("clientId") Long clientId, @Param("adminId") Long adminId);
    
    boolean existsByClientIdAndAdminId(Long clientId, Long adminId);

    // Ajoutez ces méthodes dans ClientSharedAgencyRepository.java

    @Query("SELECT COUNT(csa) FROM ClientSharedAgency csa WHERE csa.admin.id = :adminId")
    long countByAdminId(@Param("adminId") Long adminId);

    @Query("SELECT csa.client.id FROM ClientSharedAgency csa WHERE csa.admin.id = :adminId")
    List<Long> findClientIdsByAdminId(@Param("adminId") Long adminId);

}