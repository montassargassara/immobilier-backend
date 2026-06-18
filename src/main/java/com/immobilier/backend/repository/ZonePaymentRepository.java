package com.immobilier.backend.repository;

import com.immobilier.backend.entity.ZonePaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ZonePaymentRepository extends JpaRepository<ZonePaymentRequest, Long> {

    List<ZonePaymentRequest> findByAffiliateIdOrderByCreatedAtDesc(Long affiliateId);

    List<ZonePaymentRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<ZonePaymentRequest> findAllByOrderByCreatedAtDesc();

    @Query("SELECT z FROM ZonePaymentRequest z WHERE z.affiliate.id = :affiliateId AND z.status = 'PENDING' AND LOWER(z.country) = LOWER(:country) AND LOWER(z.city) = LOWER(:city)")
    List<ZonePaymentRequest> findPendingForAffiliateAndZone(
            @Param("affiliateId") Long affiliateId,
            @Param("country") String country,
            @Param("city") String city);

    boolean existsByAffiliateIdAndProofImagePath(Long affiliateId, String proofImagePath);
}
