package com.immobilier.backend.repository;

import com.immobilier.backend.entity.SaleValidationRequest;
import com.immobilier.backend.enums.PendingSaleApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SaleValidationRequestRepository extends JpaRepository<SaleValidationRequest, Long> {

    /** Agency admin: all pending requests for properties they own. */
    @Query("SELECT r FROM SaleValidationRequest r WHERE r.property.agencyAdmin.id = :adminId AND r.status = 'PENDING'")
    List<SaleValidationRequest> findPendingForAgencyAdmin(@Param("adminId") Long adminId);

    /** Super admin: all pending requests for SUPER_ADMIN_OWNED properties. */
    @Query("SELECT r FROM SaleValidationRequest r WHERE r.property.ownerType = 'SUPER_ADMIN_OWNED' AND r.status = 'PENDING'")
    List<SaleValidationRequest> findPendingForSuperAdmin();

    /** Requester: their own requests (any status). */
    List<SaleValidationRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    /** Check for an active PENDING validation on a given property. */
    Optional<SaleValidationRequest> findByPropertyIdAndStatus(Long propertyId, PendingSaleApprovalStatus status);

    /** Count pending validations for an agency admin's properties (sidebar badge). */
    @Query("SELECT COUNT(r) FROM SaleValidationRequest r WHERE r.property.agencyAdmin.id = :adminId AND r.status = 'PENDING'")
    long countPendingForAgencyAdmin(@Param("adminId") Long adminId);

    /** Count pending validations for SUPER_ADMIN (sidebar badge). */
    @Query("SELECT COUNT(r) FROM SaleValidationRequest r WHERE r.property.ownerType = 'SUPER_ADMIN_OWNED' AND r.status = 'PENDING'")
    long countPendingForSuperAdmin();

    /** Batch: IDs of properties that have at least one PENDING validation request. */
    @Query("SELECT DISTINCT r.property.id FROM SaleValidationRequest r WHERE r.property.id IN :propertyIds AND r.status = 'PENDING'")
    java.util.Set<Long> findPropertyIdsWithPendingValidation(@Param("propertyIds") java.util.List<Long> propertyIds);
}
