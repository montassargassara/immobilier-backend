package com.immobilier.backend.repository;

import com.immobilier.backend.entity.PropertyShareRequest;
import com.immobilier.backend.enums.ShareRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyShareRequestRepository extends JpaRepository<PropertyShareRequest, Long> {

    List<PropertyShareRequest> findByAgencyAdminIdOrderByCreatedAtDesc(Long agencyAdminId);

    List<PropertyShareRequest> findByAgencyAdminIdAndStatusOrderByCreatedAtDesc(
            Long agencyAdminId, ShareRequestStatus status);

    List<PropertyShareRequest> findByPropertyIdOrderByCreatedAtDesc(Long propertyId);

    List<PropertyShareRequest> findBySharedByIdOrderByCreatedAtDesc(Long sharedById);

    Optional<PropertyShareRequest> findByPropertyIdAndAgencyAdminId(Long propertyId, Long agencyAdminId);

    boolean existsByPropertyIdAndAgencyAdminIdAndStatus(
            Long propertyId, Long agencyAdminId, ShareRequestStatus status);

    long countByAgencyAdminIdAndStatus(Long agencyAdminId, ShareRequestStatus status);

    long countBySharedByIdAndStatus(Long sharedById, ShareRequestStatus status);

    @Query("SELECT r FROM PropertyShareRequest r WHERE r.agencyAdmin.id = :adminId AND r.status = 'PENDING' ORDER BY r.createdAt DESC")
    List<PropertyShareRequest> findPendingForAgency(@Param("adminId") Long adminId);

    @Query("SELECT r FROM PropertyShareRequest r WHERE r.sharedBy.id = :superAdminId ORDER BY r.createdAt DESC")
    List<PropertyShareRequest> findAllBySuperAdmin(@Param("superAdminId") Long superAdminId);
}
