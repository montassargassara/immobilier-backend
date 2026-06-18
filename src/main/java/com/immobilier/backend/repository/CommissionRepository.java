package com.immobilier.backend.repository;

import com.immobilier.backend.entity.Commission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommissionRepository extends JpaRepository<Commission, Long> {

    boolean existsByPropertyIdAndBeneficiaryTypeAndBeneficiaryId(
            Long propertyId, String beneficiaryType, Long beneficiaryId);

    List<Commission> findByBeneficiaryIdOrderByCreatedAtDesc(Long beneficiaryId);

    List<Commission> findByBeneficiaryTypeOrderByCreatedAtDesc(String beneficiaryType);

    List<Commission> findByBeneficiaryTypeAndStatusOrderByCreatedAtDesc(String beneficiaryType, String status);

    // ── Global aggregates (Super Admin) ───────────────────────────────────────

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.beneficiaryType = :type")
    Double sumByType(@Param("type") String type);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.beneficiaryType = :type AND c.status = :status")
    Double sumByTypeAndStatus(@Param("type") String type, @Param("status") String status);

    @Query("SELECT COUNT(c) FROM Commission c WHERE c.beneficiaryType = :type AND c.status = :status")
    long countByTypeAndStatus(@Param("type") String type, @Param("status") String status);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.status = :status")
    Double sumByStatus(@Param("status") String status);

    // ── Agency-scoped aggregates (Admin sees only their own staff) ────────────

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c " +
           "WHERE c.beneficiaryType = 'STAFF' AND c.status = :status " +
           "AND (c.beneficiary.parent.id = :adminId OR c.beneficiary.parent.parent.id = :adminId)")
    Double sumStaffByAgencyAdminAndStatus(@Param("adminId") Long adminId, @Param("status") String status);

    @Query("SELECT c FROM Commission c " +
           "WHERE c.beneficiaryType = 'STAFF' " +
           "AND (c.beneficiary.parent.id = :adminId OR c.beneficiary.parent.parent.id = :adminId) " +
           "ORDER BY c.createdAt DESC")
    List<Commission> findStaffByAgencyAdmin(@Param("adminId") Long adminId);

    // ── Per-beneficiary aggregates ────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.beneficiary.id = :userId AND c.status = :status")
    Double sumByBeneficiaryAndStatus(@Param("userId") Long userId, @Param("status") String status);

    long countByBeneficiaryId(Long beneficiaryId);

    @Query("SELECT COUNT(c) FROM Commission c WHERE c.beneficiary.id = :userId AND c.transactionType = :type")
    long countByBeneficiaryAndType(@Param("userId") Long userId, @Param("type") String type);

    // ── Staff ranking (top-performing internal staff) ────────────────────────
    // row = [User beneficiary, salesCount, totalCommission, pendingCommission]

    @Query("SELECT c.beneficiary, COUNT(c), COALESCE(SUM(c.commissionAmount),0), " +
           "COALESCE(SUM(CASE WHEN c.status = 'PENDING' THEN c.commissionAmount ELSE 0 END),0) " +
           "FROM Commission c WHERE c.beneficiaryType = 'STAFF' " +
           "GROUP BY c.beneficiary ORDER BY SUM(c.commissionAmount) DESC")
    List<Object[]> staffRankingGlobal(Pageable pageable);

    @Query("SELECT c.beneficiary, COUNT(c), COALESCE(SUM(c.commissionAmount),0), " +
           "COALESCE(SUM(CASE WHEN c.status = 'PENDING' THEN c.commissionAmount ELSE 0 END),0) " +
           "FROM Commission c WHERE c.beneficiaryType = 'STAFF' " +
           "AND (c.beneficiary.parent.id = :adminId OR c.beneficiary.parent.parent.id = :adminId) " +
           "GROUP BY c.beneficiary ORDER BY SUM(c.commissionAmount) DESC")
    List<Object[]> staffRankingByAgency(@Param("adminId") Long adminId, Pageable pageable);

    // ── History / list views ─────────────────────────────────────────────────

    List<Commission> findByStatusOrderByCreatedAtDesc(String status);

    @Query("SELECT c FROM Commission c WHERE c.beneficiary.id = :userId AND c.transactionType IS NOT NULL " +
           "ORDER BY c.createdAt DESC")
    List<Commission> findHistoryForBeneficiary(@Param("userId") Long userId);

    @Query("SELECT c FROM Commission c WHERE " +
           "(c.beneficiaryType = 'AGENCY' AND c.beneficiary.id = :adminId) OR " +
           "(c.beneficiaryType = 'STAFF' AND (c.beneficiary.parent.id = :adminId OR c.beneficiary.parent.parent.id = :adminId)) " +
           "ORDER BY c.createdAt DESC")
    List<Commission> findScopedForAgency(@Param("adminId") Long adminId);

    // ── Detailed list views (fetch-join — no N+1 on beneficiary/property/buyer) ─

    @Query("SELECT c FROM Commission c " +
           "JOIN FETCH c.beneficiary LEFT JOIN FETCH c.property p LEFT JOIN FETCH p.buyer " +
           "WHERE c.beneficiaryType = :type ORDER BY c.createdAt DESC")
    List<Commission> findDetailedByType(@Param("type") String type);

    @Query("SELECT c FROM Commission c " +
           "JOIN FETCH c.beneficiary LEFT JOIN FETCH c.property p LEFT JOIN FETCH p.buyer " +
           "WHERE c.beneficiaryType = :type AND c.beneficiary.id = :beneficiaryId " +
           "ORDER BY c.createdAt DESC")
    List<Commission> findDetailedByTypeAndBeneficiary(@Param("type") String type,
                                                      @Param("beneficiaryId") Long beneficiaryId);

    @Query("SELECT c FROM Commission c " +
           "JOIN FETCH c.beneficiary LEFT JOIN FETCH c.property p LEFT JOIN FETCH p.buyer " +
           "WHERE c.beneficiaryType = 'STAFF' " +
           "AND (c.beneficiary.parent.id = :adminId OR c.beneficiary.parent.parent.id = :adminId) " +
           "ORDER BY c.createdAt DESC")
    List<Commission> findDetailedStaffByAgencyAdmin(@Param("adminId") Long adminId);

    // ── Manager (RESPONSABLE_COMMERCIAL) scope: self + descendant COMMERCIALs ──

    @Query("SELECT c FROM Commission c " +
           "JOIN FETCH c.beneficiary LEFT JOIN FETCH c.property p LEFT JOIN FETCH p.buyer " +
           "WHERE c.beneficiaryType = 'STAFF' " +
           "AND (c.beneficiary.id = :managerId " +
           "  OR c.beneficiary.parent.id = :managerId " +
           "  OR c.beneficiary.parent.parent.id = :managerId) " +
           "ORDER BY c.createdAt DESC")
    List<Commission> findDetailedStaffForManager(@Param("managerId") Long managerId);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c " +
           "WHERE c.beneficiaryType = 'STAFF' AND c.status = :status " +
           "AND (c.beneficiary.id = :managerId " +
           "  OR c.beneficiary.parent.id = :managerId " +
           "  OR c.beneficiary.parent.parent.id = :managerId)")
    Double sumStaffByManagerAndStatus(@Param("managerId") Long managerId,
                                      @Param("status") String status);

    // ── Scoped aggregates by beneficiary + type (Agency admin sees only own) ───

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c " +
           "WHERE c.beneficiaryType = :type AND c.beneficiary.id = :beneficiaryId AND c.status = :status")
    Double sumByTypeBeneficiaryAndStatus(@Param("type") String type,
                                         @Param("beneficiaryId") Long beneficiaryId,
                                         @Param("status") String status);

    @Query("SELECT COUNT(c) FROM Commission c " +
           "WHERE c.beneficiaryType = :type AND c.beneficiary.id = :beneficiaryId")
    long countByTypeAndBeneficiary(@Param("type") String type,
                                   @Param("beneficiaryId") Long beneficiaryId);
}
