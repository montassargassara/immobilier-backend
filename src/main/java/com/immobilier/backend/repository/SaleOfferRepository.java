package com.immobilier.backend.repository;

import com.immobilier.backend.entity.SaleOffer;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.SaleOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SaleOfferRepository extends JpaRepository<SaleOffer, Long> {

    // ── Affiliate view ───────────────────────────────────────────────────────
    List<SaleOffer> findByAffiliateOrderByCreatedAtDesc(User affiliate);

    List<SaleOffer> findByAffiliateIdOrderByCreatedAtDesc(Long affiliateId);

    List<SaleOffer> findByAffiliateIdAndStatusOrderByCreatedAtDesc(Long affiliateId, SaleOfferStatus status);

    // ── Agency view (offers on properties owned by that agency admin) ────────
    @Query("SELECT o FROM SaleOffer o WHERE o.property.agencyAdmin.id = :agencyAdminId ORDER BY o.createdAt DESC")
    List<SaleOffer> findByAgencyAdminIdOrderByCreatedAtDesc(@Param("agencyAdminId") Long agencyAdminId);

    @Query("SELECT o FROM SaleOffer o WHERE o.property.agencyAdmin.id = :agencyAdminId AND o.status = :status ORDER BY o.createdAt DESC")
    List<SaleOffer> findByAgencyAdminIdAndStatusOrderByCreatedAtDesc(@Param("agencyAdminId") Long agencyAdminId,
                                                                      @Param("status") SaleOfferStatus status);

    // ── Super Admin / global view ────────────────────────────────────────────
    List<SaleOffer> findAllByOrderByCreatedAtDesc();

    List<SaleOffer> findByStatusOrderByCreatedAtDesc(SaleOfferStatus status);

    // ── Duplicate guard: prevent same affiliate submitting multiple PENDING offers on same property ──
    boolean existsByAffiliateIdAndPropertyIdAndStatus(Long affiliateId, Long propertyId, SaleOfferStatus status);

    // ── Stats / aggregations ─────────────────────────────────────────────────
    long countByAffiliateId(Long affiliateId);

    long countByAffiliateIdAndStatus(Long affiliateId, SaleOfferStatus status);

    @Query("SELECT COUNT(o) FROM SaleOffer o WHERE o.affiliate.id = :affiliateId AND o.status = 'COMPLETED' AND o.createdAt >= :since")
    long countCompletedSince(@Param("affiliateId") Long affiliateId, @Param("since") LocalDateTime since);

    /** All accepted or completed offers — used to compute per-zone demand scores. */
    @Query("SELECT o FROM SaleOffer o WHERE o.status IN ('ACCEPTED', 'COMPLETED')")
    List<SaleOffer> findAcceptedOrCompletedOffers();

    // ── Agency-scoped affiliate queries ───────────────────────────────────────

    @Query("SELECT DISTINCT o.affiliate FROM SaleOffer o WHERE o.property.agencyAdmin.id = :agencyAdminId")
    List<User> findDistinctAffiliatesForAgency(@Param("agencyAdminId") Long agencyAdminId);

    @Query("SELECT COUNT(o) FROM SaleOffer o WHERE o.affiliate.id = :affiliateId AND o.property.agencyAdmin.id = :agencyAdminId")
    long countOffersForAffiliateAndAgency(@Param("affiliateId") Long affiliateId,
                                          @Param("agencyAdminId") Long agencyAdminId);
}
