package com.immobilier.backend.repository;

import com.immobilier.backend.entity.AffiliateCustomerRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AffiliateCustomerRelationRepository
        extends JpaRepository<AffiliateCustomerRelation, Long> {

    boolean existsBySaleOfferId(Long saleOfferId);

    Optional<AffiliateCustomerRelation> findFirstByPropertyIdOrderByCreatedAtDesc(Long propertyId);

    Optional<AffiliateCustomerRelation> findByAffiliateTransactionId(Long affiliateTransactionId);

    List<AffiliateCustomerRelation> findAllByOrderByCreatedAtDesc();

    List<AffiliateCustomerRelation> findByAffiliateIdOrderByCreatedAtDesc(Long affiliateId);

    long countByAffiliateId(Long affiliateId);

    // ── BI aggregates ────────────────────────────────────────────────────────

    @Query("SELECT COUNT(r) FROM AffiliateCustomerRelation r")
    long countAll();

    @Query("SELECT COALESCE(SUM(r.propertyPrice), 0) FROM AffiliateCustomerRelation r")
    Double getTotalRevenueViaAffiliates();

    @Query("SELECT COUNT(r) FROM AffiliateCustomerRelation r WHERE r.transactionType = :type")
    long countByTransactionType(@Param("type") String type);
}
