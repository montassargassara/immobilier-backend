package com.immobilier.backend.repository;


import com.immobilier.backend.entity.AffiliateTransaction;
import com.immobilier.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AffiliateTransactionRepository extends JpaRepository<AffiliateTransaction, Long> {
    
    List<AffiliateTransaction> findByAffiliateOrderByTransactionDateDesc(User affiliate);

    List<AffiliateTransaction> findByAffiliateIdOrderByTransactionDateDesc(Long affiliateId);

    List<AffiliateTransaction> findByAffiliateIdAndIsPaid(Long affiliateId, Boolean isPaid);
    
    @Query("SELECT SUM(at.commissionAmount) FROM AffiliateTransaction at WHERE at.affiliate = :affiliate AND at.isPaid = true")
    Double getTotalPaidCommission(@Param("affiliate") User affiliate);
    
    @Query("SELECT SUM(at.commissionAmount) FROM AffiliateTransaction at WHERE at.affiliate = :affiliate AND at.isPaid = false")
    Double getTotalPendingCommission(@Param("affiliate") User affiliate);
    
    @Query("SELECT COUNT(at) FROM AffiliateTransaction at WHERE at.affiliate = :affiliate AND at.transactionDate >= :startDate")
    Long countSalesSince(@Param("affiliate") User affiliate, @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT at.affiliate, COUNT(at) as saleCount, SUM(at.commissionAmount) as totalCommission " +
           "FROM AffiliateTransaction at WHERE at.transactionDate >= :startDate " +
           "GROUP BY at.affiliate ORDER BY COUNT(at) DESC")
    List<Object[]> getAffiliateRanking(@Param("startDate") LocalDateTime startDate);
    
    // Legacy — keeps paid-only ranking for payout reports
    @Query("SELECT at.affiliate, COUNT(at) as saleCount, SUM(at.commissionAmount) as totalCommission " +
           "FROM AffiliateTransaction at WHERE at.transactionDate >= :startDate AND at.isPaid = true " +
           "GROUP BY at.affiliate ORDER BY SUM(at.commissionAmount) DESC")
    List<Object[]> getAffiliateRankingByRevenue(@Param("startDate") LocalDateTime startDate);

    // Ranking counts ALL transactions (paid and unpaid) — used for monthly leaderboard and bonus calculation
    @Query("SELECT at.affiliate, COUNT(at) as saleCount, SUM(at.commissionAmount) as totalCommission " +
           "FROM AffiliateTransaction at WHERE at.transactionDate >= :startDate " +
           "GROUP BY at.affiliate ORDER BY SUM(at.commissionAmount) DESC")
    List<Object[]> getRankingByAllCommissions(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT at FROM AffiliateTransaction at WHERE at.transactionDate BETWEEN :startDate AND :endDate")
    List<AffiliateTransaction> findByTransactionDateBetween(@Param("startDate") LocalDateTime startDate,
                                                            @Param("endDate") LocalDateTime endDate);

    // ── Agency-scoped queries ─────────────────────────────────────────────────

    @Query("SELECT at FROM AffiliateTransaction at WHERE at.property.agencyAdmin.id = :agencyAdminId ORDER BY at.transactionDate DESC")
    List<AffiliateTransaction> findByAgencyAdminIdOrderByDateDesc(@Param("agencyAdminId") Long agencyAdminId);

    @Query("SELECT at FROM AffiliateTransaction at WHERE at.affiliate.id = :affiliateId AND at.property.agencyAdmin.id = :agencyAdminId ORDER BY at.transactionDate DESC")
    List<AffiliateTransaction> findByAffiliateIdAndAgencyAdminId(@Param("affiliateId") Long affiliateId,
                                                                  @Param("agencyAdminId") Long agencyAdminId);

    // ── Per-affiliate aggregates (used by ClientManagementService to populate ClientDTO) ───────────

    long countByAffiliateId(Long affiliateId);

    @Query("SELECT COALESCE(SUM(at.commissionAmount), 0) FROM AffiliateTransaction at WHERE at.affiliate.id = :affiliateId")
    Double getTotalCommissionsByAffiliateId(@Param("affiliateId") Long affiliateId);

    // ── BI analytics ─────────────────────────────────────────────────────────
    @Query("SELECT COALESCE(SUM(at.commissionAmount), 0) FROM AffiliateTransaction at")
    Double getTotalCommissions();

    @Query("SELECT COALESCE(SUM(at.commissionAmount), 0) FROM AffiliateTransaction at WHERE at.transactionDate >= :since")
    Double getTotalCommissionsSince(@Param("since") java.time.LocalDateTime since);

    @Query("SELECT COALESCE(SUM(at.commissionAmount), 0) FROM AffiliateTransaction at WHERE at.property.ownerType = 'SUPER_ADMIN_OWNED'")
    Double getTotalCommissionsSuperAdminOwned();

    @Query("SELECT COALESCE(SUM(at.commissionAmount), 0) FROM AffiliateTransaction at WHERE at.property.ownerType = 'AGENCY_OWNED' OR at.property.ownerType IS NULL")
    Double getTotalCommissionsAgencyOwned();

    @Query("SELECT COALESCE(SUM(at.commissionAmount), 0) FROM AffiliateTransaction at WHERE at.isPaid = false")
    Double getTotalUnpaidCommissions();

    @Query("SELECT COUNT(at) FROM AffiliateTransaction at WHERE at.isPaid = false")
    long countUnpaidTransactions();

    long countByIsPaid(Boolean isPaid);

    @Query("SELECT COALESCE(SUM(at.commissionAmount), 0) FROM AffiliateTransaction at WHERE at.isPaid = true")
    Double getTotalPaidCommissions();

    @Query("SELECT MONTH(at.transactionDate), YEAR(at.transactionDate), COALESCE(SUM(at.commissionAmount), 0) " +
           "FROM AffiliateTransaction at WHERE at.transactionDate >= :since " +
           "GROUP BY YEAR(at.transactionDate), MONTH(at.transactionDate) " +
           "ORDER BY YEAR(at.transactionDate), MONTH(at.transactionDate)")
    List<Object[]> getMonthlyCommissionsSince(@Param("since") java.time.LocalDateTime since);

    // ── Agency-scoped BI queries ──────────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(at.commissionAmount), 0) FROM AffiliateTransaction at WHERE at.property.agencyAdmin.id = :adminId")
    Double getTotalCommissionsByAgencyAdmin(@Param("adminId") Long adminId);

    @Query("SELECT COALESCE(SUM(at.commissionAmount), 0) FROM AffiliateTransaction at WHERE at.property.agencyAdmin.id = :adminId AND at.transactionDate >= :since")
    Double getTotalCommissionsSinceByAgencyAdmin(@Param("adminId") Long adminId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT MONTH(at.transactionDate), YEAR(at.transactionDate), COALESCE(SUM(at.commissionAmount), 0) " +
           "FROM AffiliateTransaction at WHERE at.property.agencyAdmin.id = :adminId AND at.transactionDate >= :since " +
           "GROUP BY YEAR(at.transactionDate), MONTH(at.transactionDate) " +
           "ORDER BY YEAR(at.transactionDate), MONTH(at.transactionDate)")
    List<Object[]> getMonthlyCommissionsSinceByAgencyAdmin(@Param("adminId") Long adminId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT at.affiliate, COUNT(at) as saleCount, SUM(at.commissionAmount) as totalCommission " +
           "FROM AffiliateTransaction at WHERE at.property.agencyAdmin.id = :adminId AND at.transactionDate >= :startDate " +
           "GROUP BY at.affiliate ORDER BY COUNT(at) DESC")
    List<Object[]> getAffiliateRankingByAgencyAdmin(@Param("adminId") Long adminId, @Param("startDate") java.time.LocalDateTime startDate);

    @Query("SELECT COALESCE(SUM(at.commissionAmount), 0) FROM AffiliateTransaction at WHERE at.property.agencyAdmin.id = :adminId AND at.isPaid = false")
    Double getTotalUnpaidCommissionsByAgencyAdmin(@Param("adminId") Long adminId);

    @Query("SELECT COUNT(at) FROM AffiliateTransaction at WHERE at.property.agencyAdmin.id = :adminId AND at.isPaid = false")
    long countUnpaidTransactionsByAgencyAdmin(@Param("adminId") Long adminId);

    @Query("SELECT COUNT(DISTINCT at.affiliate.id) FROM AffiliateTransaction at WHERE at.property.agencyAdmin.id = :adminId")
    long countDistinctAffiliatesByAgencyAdmin(@Param("adminId") Long adminId);
}
