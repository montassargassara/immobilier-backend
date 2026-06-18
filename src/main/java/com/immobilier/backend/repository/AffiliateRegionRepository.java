package com.immobilier.backend.repository;

import com.immobilier.backend.entity.AffiliateRegion;
import com.immobilier.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AffiliateRegionRepository extends JpaRepository<AffiliateRegion, Long> {
    
    List<AffiliateRegion> findByAffiliate(User affiliate);
    
    List<AffiliateRegion> findByAffiliateIdAndIsActiveTrue(Long affiliateId);
    
    Optional<AffiliateRegion> findByAffiliateIdAndRegionName(Long affiliateId, String regionName);
    
    @Query("SELECT ar FROM AffiliateRegion ar WHERE ar.regionName = :regionName AND ar.isActive = true")
    List<AffiliateRegion> findActiveByRegionName(@Param("regionName") String regionName);
    
    @Query("SELECT ar.regionName, COUNT(ar) FROM AffiliateRegion ar WHERE ar.isActive = true GROUP BY ar.regionName")
    List<Object[]> getActiveZoneCountByRegion();
    
    @Query("SELECT ar FROM AffiliateRegion ar WHERE ar.affiliate = :affiliate ORDER BY ar.createdAt DESC")
    List<AffiliateRegion> findByAffiliateOrderByCreatedAtDesc(@Param("affiliate") User affiliate);
}
