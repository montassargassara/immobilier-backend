package com.immobilier.backend.repository;

import com.immobilier.backend.entity.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    // ========== BASIC QUERIES ==========
    
    Page<Property> findByIsActiveTrue(Pageable pageable);
    
    List<Property> findByIsActiveTrue();
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true ORDER BY p.createdAt DESC")
    List<Property> findRecentProperties(@Param("limit") int limit);
    
    // ========== COUNT METHODS ==========
    
    // Count by statut - for dashboard stats
    long countByStatut(String statut);
    
    // Alternative count method
    @Query("SELECT COUNT(p) FROM Property p WHERE p.statut = :statut")
    long countPropertiesByStatut(@Param("statut") String statut);
    
    // ========== SEARCH AND FILTER METHODS ==========
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND " +
           "(LOWER(p.titre) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.adresse) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Property> searchProperties(@Param("keyword") String keyword, Pageable pageable);
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.type = :type")
    List<Property> findByType(@Param("type") String type);
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.statut = :statut")
    List<Property> findByStatut(@Param("statut") String statut);

    @Query("SELECT p FROM Property p WHERE p.buyer.id = :buyerId ORDER BY p.updatedAt DESC")
    List<Property> findByBuyerIdOrderByUpdatedAtDesc(@Param("buyerId") Long buyerId);
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.prixVente BETWEEN :minPrice AND :maxPrice")
    List<Property> findByPriceRange(@Param("minPrice") Double minPrice, @Param("maxPrice") Double maxPrice);
    
    // ========== AGGREGATION QUERIES ==========
    
    @Query("SELECT COUNT(p) FROM Property p WHERE p.isActive = true")
    long countActiveProperties();
    
    @Query("SELECT p.type, COUNT(p) FROM Property p WHERE p.isActive = true GROUP BY p.type")
    List<Object[]> getPropertyTypeStats();
    
    @Query("SELECT p.statut, COUNT(p) FROM Property p WHERE p.isActive = true GROUP BY p.statut")
    List<Object[]> getPropertyStatusStats();
    
    // For dashboard - find popular areas
    @Query("SELECT p.adresse, COUNT(p) FROM Property p GROUP BY p.adresse ORDER BY COUNT(p) DESC")
    List<Object[]> findPopularAreas();
    
    // For dashboard - find property types distribution
    @Query("SELECT p.type, COUNT(p) FROM Property p GROUP BY p.type")
    List<Object[]> findPropertyTypes();
    
    // ========== REVENUE CALCULATIONS ==========
    
    // Calculate monthly revenue (last 30 days)
    @Query("SELECT COALESCE(SUM(p.prixVente), 0) FROM Property p WHERE p.statut = 'VENDU' AND p.updatedAt >= :startDate")
    Double calculateMonthlyRevenue(@Param("startDate") java.time.LocalDateTime startDate);
    
    // Calculate yearly revenue (last 365 days)
    @Query("SELECT COALESCE(SUM(p.prixVente), 0) FROM Property p WHERE p.statut = 'VENDU' AND p.updatedAt >= :startDate")
    Double calculateYearlyRevenue(@Param("startDate") java.time.LocalDateTime startDate);
    
    // Default methods without parameters (for backward compatibility)
    default Double calculateMonthlyRevenue() {
        java.time.LocalDateTime oneMonthAgo = java.time.LocalDateTime.now().minusDays(30);
        return calculateMonthlyRevenue(oneMonthAgo);
    }
    
    default Double calculateYearlyRevenue() {
        java.time.LocalDateTime oneYearAgo = java.time.LocalDateTime.now().minusDays(365);
        return calculateYearlyRevenue(oneYearAgo);
    }
    
    // Alternative revenue calculation with direct query
    @Query("SELECT COALESCE(SUM(p.prixVente), 0) FROM Property p WHERE p.statut = 'VENDU'")
    Double calculateTotalRevenue();
    
    @Query("SELECT COALESCE(SUM(p.prixVente), 0) FROM Property p WHERE p.statut = 'VENDU' AND YEAR(p.updatedAt) = :year")
    Double calculateRevenueByYear(@Param("year") int year);
    
    @Query("SELECT COALESCE(SUM(p.prixVente), 0) FROM Property p WHERE p.statut = 'VENDU' AND MONTH(p.updatedAt) = :month AND YEAR(p.updatedAt) = :year")
    Double calculateRevenueByMonth(@Param("month") int month, @Param("year") int year);

    // ── Revenue split by ownership (BI revenue separation) ──────────────────
    // Rentals count prixLocation; sales count prixVente. Legacy NULL ownerType is treated as agency-owned.

    @Query("SELECT COALESCE(SUM(p.prixLocation), 0) FROM Property p WHERE p.statut = 'LOUE'")
    Double sumRentalRevenueAll();

    @Query("SELECT COALESCE(SUM(p.prixVente), 0) FROM Property p WHERE p.statut = 'VENDU' AND p.ownerType = 'SUPER_ADMIN_OWNED'")
    Double sumSalesRevenueSuperAdminOwned();

    @Query("SELECT COALESCE(SUM(p.prixLocation), 0) FROM Property p WHERE p.statut = 'LOUE' AND p.ownerType = 'SUPER_ADMIN_OWNED'")
    Double sumRentalRevenueSuperAdminOwned();

    @Query("SELECT COALESCE(SUM(p.prixVente), 0) FROM Property p WHERE p.statut = 'VENDU' AND (p.ownerType = 'AGENCY_OWNED' OR p.ownerType IS NULL)")
    Double sumSalesRevenueAgencyOwned();

    @Query("SELECT COALESCE(SUM(p.prixLocation), 0) FROM Property p WHERE p.statut = 'LOUE' AND (p.ownerType = 'AGENCY_OWNED' OR p.ownerType IS NULL)")
    Double sumRentalRevenueAgencyOwned();
    
    // ========== AVERAGE PRICE QUERIES ==========
    
    @Query("SELECT COALESCE(AVG(p.prixVente), 0) FROM Property p WHERE p.isActive = true AND p.type = :type")
    Double getAveragePriceByType(@Param("type") String type);
    
    // ========== GEO-SPATIAL QUERIES ==========
    
    @Query(value = "SELECT p.* FROM properties p WHERE p.is_active = true AND " +
           "ST_Distance_Sphere(point(p.longitude, p.latitude), point(:lng, :lat)) <= :radius",
           nativeQuery = true)
    List<Property> findPropertiesWithinRadius(@Param("lat") Double lat, 
                                              @Param("lng") Double lng, 
                                              @Param("radius") Double radius);
    
    // ========== TIME-BASED QUERIES ==========
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.createdAt >= :startDate ORDER BY p.createdAt DESC")
    List<Property> findRecentPropertiesByDate(@Param("startDate") java.time.LocalDateTime startDate);
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.statut = 'VENDU' AND p.updatedAt >= :startDate")
    List<Property> findSoldPropertiesSince(@Param("startDate") java.time.LocalDateTime startDate);
    
    // ========== STATISTICS QUERIES ==========
    
    @Query("SELECT DATE(p.createdAt), COUNT(p) FROM Property p WHERE p.createdAt >= :startDate GROUP BY DATE(p.createdAt)")
    List<Object[]> getDailyPropertyStats(@Param("startDate") java.time.LocalDateTime startDate);
    
    @Query("SELECT MONTH(p.createdAt), COUNT(p) FROM Property p WHERE YEAR(p.createdAt) = :year GROUP BY MONTH(p.createdAt)")
    List<Object[]> getMonthlyPropertyStats(@Param("year") int year);
    
    @Query("SELECT p.type, AVG(p.prixVente), AVG(p.surface) FROM Property p WHERE p.isActive = true GROUP BY p.type")
    List<Object[]> getAveragePriceAndSurfaceByType();
   
    // ========== REGION-BASED QUERIES ==========
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.region = :region")
    List<Property> findByRegion(@Param("region") String region);
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.region IN :regions")
    List<Property> findByRegions(@Param("regions") List<String> regions);
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND " +
           "(LOWER(p.adresse) LIKE LOWER(CONCAT('%', :area, '%')) OR " +
           "LOWER(p.region) LIKE LOWER(CONCAT('%', :area, '%')))")
    List<Property> findByArea(@Param("area") String area);
    
    
    // ========== COMMISSION QUERIES ==========
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.commissionPercentage IS NOT NULL")
    List<Property> findPropertiesWithCommission();
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.commissionPercentage >= :minCommission")
    List<Property> findByMinCommission(@Param("minCommission") Double minCommission);
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.commissionPercentage BETWEEN :min AND :max")
    List<Property> findByCommissionRange(@Param("min") Double min, @Param("max") Double max);
    
    @Query("SELECT AVG(p.commissionPercentage) FROM Property p WHERE p.isActive = true")
    Double getAverageCommission();
    
    @Query("SELECT p.type, AVG(p.commissionPercentage) FROM Property p WHERE p.isActive = true GROUP BY p.type")
    List<Object[]> getAverageCommissionByType();
    
    @Query("SELECT p.region, AVG(p.commissionPercentage) FROM Property p WHERE p.isActive = true GROUP BY p.region")
    List<Object[]> getAverageCommissionByRegion();
    
    // ========== ADVANCED FILTER WITH REGION AND COMMISSION ==========
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true " +
           "AND (:country IS NULL OR p.country = :country) " +
           "AND (:city IS NULL OR p.city = :city) " +
           "AND (:region IS NULL OR p.region = :region) " +
           "AND (:minPrice IS NULL OR p.prixVente >= :minPrice) " +
           "AND (:maxPrice IS NULL OR p.prixVente <= :maxPrice) " +
           "AND (:minCommission IS NULL OR p.commissionPercentage >= :minCommission) " +
           "AND (:propertyType IS NULL OR p.type = :propertyType)")
    List<Property> findWithFilters(@Param("country") String country,
                                   @Param("city") String city,
                                   @Param("region") String region,
                                   @Param("minPrice") Double minPrice,
                                   @Param("maxPrice") Double maxPrice,
                                   @Param("minCommission") Double minCommission,
                                   @Param("propertyType") String propertyType);
                                

    
    // ========== LOCATION-BASED QUERIES ==========
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.country = :country")
    List<Property> findByCountry(@Param("country") String country);
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.country = :country AND p.city = :city")
    List<Property> findByCountryAndCity(@Param("country") String country, @Param("city") String city);
    
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.city = :city")
    List<Property> findByCity(@Param("city") String city);
    
    @Query("SELECT DISTINCT p.country FROM Property p WHERE p.isActive = true AND p.country IS NOT NULL")
    List<String> findAllCountries();
    
    @Query("SELECT DISTINCT p.city FROM Property p WHERE p.isActive = true AND p.country = :country AND p.city IS NOT NULL")
    List<String> findCitiesByCountry(@Param("country") String country);

    @Query("SELECT DISTINCT p.region FROM Property p WHERE p.region IS NOT NULL AND p.isActive = true")
    List<String> findAllRegions();

    // ========== AFFILIATE VISIBILITY QUERIES ==========

    /**
     * Strict zone-key matching: each entry in :zoneKeys is "country|city" lowercase.
     * Matches a property only when BOTH country AND city equal the affiliate's pair.
     * Used by affiliates whose regions provide explicit country + city.
     */
    @Query("SELECT p FROM Property p WHERE p.isActive = true " +
           "AND p.isAffiliateEligible = true " +
           "AND p.statut = 'DISPONIBLE' " +
           "AND (p.validationStatus IS NULL OR p.validationStatus = com.immobilier.backend.enums.PropertyValidationStatus.APPROVED) " +
           "AND (p.isReservedByAffiliate IS NULL OR p.isReservedByAffiliate = false) " +
           "AND p.commissionPercentage IS NOT NULL AND p.commissionPercentage > 0 " +
           "AND p.country IS NOT NULL AND p.city IS NOT NULL " +
           "AND CONCAT(LOWER(TRIM(p.country)), '|', LOWER(TRIM(p.city))) IN :zoneKeys")
    List<Property> findEligiblePropertiesForAffiliateZoneKeys(@Param("zoneKeys") List<String> zoneKeys);

    /**
     * Legacy fallback for affiliates whose regions only provide a single name (no country/city split).
     * Matches by city or region name (case-insensitive). Kept to avoid breaking older registrations.
     */
    @Query("SELECT p FROM Property p WHERE p.isActive = true " +
           "AND p.isAffiliateEligible = true " +
           "AND p.statut = 'DISPONIBLE' " +
           "AND (p.validationStatus IS NULL OR p.validationStatus = com.immobilier.backend.enums.PropertyValidationStatus.APPROVED) " +
           "AND (p.isReservedByAffiliate IS NULL OR p.isReservedByAffiliate = false) " +
           "AND p.commissionPercentage IS NOT NULL AND p.commissionPercentage > 0 " +
           "AND (LOWER(TRIM(p.region)) IN :regions OR LOWER(TRIM(p.city)) IN :regions)")
    List<Property> findEligiblePropertiesForAffiliateRegions(@Param("regions") List<String> regions);

    /**
     * All affiliate-eligible properties across all zones — used for suggested-zone computation.
     * Excludes reserved properties.
     */
    @Query("SELECT p FROM Property p WHERE p.isActive = true " +
           "AND p.isAffiliateEligible = true " +
           "AND p.statut = 'DISPONIBLE' " +
           "AND (p.validationStatus IS NULL OR p.validationStatus = com.immobilier.backend.enums.PropertyValidationStatus.APPROVED) " +
           "AND (p.isReservedByAffiliate IS NULL OR p.isReservedByAffiliate = false) " +
           "AND p.commissionPercentage IS NOT NULL AND p.commissionPercentage > 0")
    List<Property> findAllAffiliateEligibleProperties();

    // ========== MULTI-TENANT VISIBILITY QUERIES ==========

    /**
     * Properties visible to an agency admin:
     *  - Properties directly owned by that agency (ownerType = 'AGENCY_OWNED')
     *  - Super-admin properties that have been explicitly shared with this agency
     *  - Legacy rows (ownerType IS NULL) treated as agency-owned for backward compat
     */
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND (" +
           "  p.ownerType IS NULL OR" +
           "  (p.ownerType = 'AGENCY_OWNED' AND p.agencyAdmin.id = :agencyAdminId) OR" +
           "  (p.ownerType = 'SUPER_ADMIN_OWNED' AND p.id IN (" +
           "    SELECT psa.property.id FROM PropertySharedAgency psa WHERE psa.agencyAdmin.id = :agencyAdminId" +
           "  ))" +
           ")")
    List<Property> findVisiblePropertiesForAgency(@Param("agencyAdminId") Long agencyAdminId);

    /**
     * Properties visible to a COMMERCIAL: only those they created within their agency.
     */
    @Query("SELECT p FROM Property p WHERE p.isActive = true " +
           "AND p.agencyAdmin.id = :agencyAdminId " +
           "AND p.createdBy.id = :userId")
    List<Property> findVisiblePropertiesForCommercial(@Param("userId") Long userId,
                                                      @Param("agencyAdminId") Long agencyAdminId);

    /** Properties owned by a specific agency admin (for ownership checks). */
    @Query("SELECT p FROM Property p WHERE p.isActive = true AND p.agencyAdmin.id = :agencyAdminId")
    List<Property> findByAgencyAdminId(@Param("agencyAdminId") Long agencyAdminId);

    /** Properties with no validation status — used by the backfill runner on startup. */
    @Query("SELECT p FROM Property p WHERE p.validationStatus IS NULL")
    List<Property> findAllNeedingValidationBackfill();

    // ========== BI / ANALYTICS QUERIES ==========

    @Query("SELECT MONTH(p.updatedAt), YEAR(p.updatedAt), COUNT(p) FROM Property p " +
           "WHERE p.statut = 'VENDU' AND p.updatedAt >= :since " +
           "GROUP BY YEAR(p.updatedAt), MONTH(p.updatedAt) ORDER BY YEAR(p.updatedAt), MONTH(p.updatedAt)")
    List<Object[]> getMonthlySalesSince(@Param("since") java.time.LocalDateTime since);

    @Query("SELECT MONTH(p.updatedAt), YEAR(p.updatedAt), COUNT(p) FROM Property p " +
           "WHERE p.statut = 'LOUE' AND p.updatedAt >= :since " +
           "GROUP BY YEAR(p.updatedAt), MONTH(p.updatedAt) ORDER BY YEAR(p.updatedAt), MONTH(p.updatedAt)")
    List<Object[]> getMonthlyRentalsSince(@Param("since") java.time.LocalDateTime since);

    @Query("SELECT MONTH(p.updatedAt), YEAR(p.updatedAt), COALESCE(SUM(p.prixVente), 0) FROM Property p " +
           "WHERE p.statut = 'VENDU' AND p.updatedAt >= :since " +
           "GROUP BY YEAR(p.updatedAt), MONTH(p.updatedAt) ORDER BY YEAR(p.updatedAt), MONTH(p.updatedAt)")
    List<Object[]> getMonthlyRevenueSince(@Param("since") java.time.LocalDateTime since);

    @Query("SELECT p.city, p.country, COUNT(p), COALESCE(SUM(p.prixVente), 0) FROM Property p " +
           "WHERE p.statut = 'VENDU' AND p.city IS NOT NULL " +
           "GROUP BY p.city, p.country ORDER BY COUNT(p) DESC")
    List<Object[]> getTopCitiesBySales(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p.city, p.country, COUNT(p) FROM Property p " +
           "WHERE p.isActive = true AND p.statut = 'DISPONIBLE' AND p.city IS NOT NULL " +
           "GROUP BY p.city, p.country ORDER BY COUNT(p) DESC")
    List<Object[]> getTopCitiesByActive(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p.type, COUNT(p), p.statut FROM Property p WHERE p.isActive = true GROUP BY p.type, p.statut")
    List<Object[]> getTypeStatsByStatus();

    @Query("SELECT p.agencyAdmin.id, p.agencyAdmin.nom, p.agencyAdmin.prenom, p.agencyAdmin.email, " +
           "COUNT(p), COALESCE(SUM(p.prixVente), 0) FROM Property p " +
           "WHERE p.statut = 'VENDU' AND p.ownerType = 'AGENCY_OWNED' AND p.agencyAdmin IS NOT NULL " +
           "GROUP BY p.agencyAdmin.id, p.agencyAdmin.nom, p.agencyAdmin.prenom, p.agencyAdmin.email " +
           "ORDER BY COUNT(p) DESC")
    List<Object[]> getAgencyRankingBySales(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p.agencyAdmin.id, COUNT(p) FROM Property p " +
           "WHERE p.isActive = true AND p.statut = 'DISPONIBLE' AND p.agencyAdmin IS NOT NULL " +
           "GROUP BY p.agencyAdmin.id")
    List<Object[]> getActiveCountByAgency();

    @Query("SELECT COUNT(p) FROM Property p " +
           "WHERE p.isActive = true AND p.statut = 'DISPONIBLE' AND p.createdAt < :threshold")
    long countStagnantProperties(@Param("threshold") java.time.LocalDateTime threshold);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.statut = 'VENDU' AND p.updatedAt >= :start AND p.updatedAt < :end")
    long countVenduBetween(@Param("start") java.time.LocalDateTime start,
                           @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.statut = 'LOUE' AND p.updatedAt >= :start AND p.updatedAt < :end")
    long countLoueBetween(@Param("start") java.time.LocalDateTime start,
                          @Param("end") java.time.LocalDateTime end);

    // ── Agency-scoped BI queries ──────────────────────────────────────────────

    @Query("SELECT COUNT(p) FROM Property p WHERE p.agencyAdmin.id = :adminId")
    long countByAgencyAdmin(@Param("adminId") Long adminId);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.agencyAdmin.id = :adminId AND p.statut = 'DISPONIBLE'")
    long countAvailableByAgencyAdmin(@Param("adminId") Long adminId);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.agencyAdmin.id = :adminId AND p.statut = 'VENDU'")
    long countSoldByAgencyAdmin(@Param("adminId") Long adminId);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.agencyAdmin.id = :adminId AND p.statut = 'LOUE'")
    long countRentedByAgencyAdmin(@Param("adminId") Long adminId);

    @Query("SELECT COALESCE(SUM(p.prixVente), 0) FROM Property p WHERE p.statut = 'VENDU' AND p.agencyAdmin.id = :adminId")
    Double calculateTotalRevenueByAgencyAdmin(@Param("adminId") Long adminId);

    @Query("SELECT COALESCE(SUM(p.prixLocation), 0) FROM Property p WHERE p.statut = 'LOUE' AND p.agencyAdmin.id = :adminId")
    Double calculateTotalRentalRevenueByAgencyAdmin(@Param("adminId") Long adminId);

    @Query("SELECT COALESCE(SUM(p.prixVente), 0) FROM Property p WHERE p.statut = 'VENDU' AND p.agencyAdmin.id = :adminId AND p.updatedAt >= :startDate")
    Double calculateMonthlyRevenueByAgencyAdmin(@Param("adminId") Long adminId, @Param("startDate") java.time.LocalDateTime startDate);

    @Query("SELECT COALESCE(SUM(p.prixVente), 0) FROM Property p WHERE p.statut = 'VENDU' AND p.agencyAdmin.id = :adminId AND MONTH(p.updatedAt) = :month AND YEAR(p.updatedAt) = :year")
    Double calculateRevenueByMonthByAgencyAdmin(@Param("adminId") Long adminId, @Param("month") int month, @Param("year") int year);

    @Query("SELECT MONTH(p.updatedAt), YEAR(p.updatedAt), COUNT(p) FROM Property p " +
           "WHERE p.statut = 'VENDU' AND p.agencyAdmin.id = :adminId AND p.updatedAt >= :since " +
           "GROUP BY YEAR(p.updatedAt), MONTH(p.updatedAt) ORDER BY YEAR(p.updatedAt), MONTH(p.updatedAt)")
    List<Object[]> getMonthlySalesByAgencyAdminSince(@Param("adminId") Long adminId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT MONTH(p.updatedAt), YEAR(p.updatedAt), COUNT(p) FROM Property p " +
           "WHERE p.statut = 'LOUE' AND p.agencyAdmin.id = :adminId AND p.updatedAt >= :since " +
           "GROUP BY YEAR(p.updatedAt), MONTH(p.updatedAt) ORDER BY YEAR(p.updatedAt), MONTH(p.updatedAt)")
    List<Object[]> getMonthlyRentalsByAgencyAdminSince(@Param("adminId") Long adminId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT MONTH(p.updatedAt), YEAR(p.updatedAt), COALESCE(SUM(p.prixVente), 0) FROM Property p " +
           "WHERE p.statut = 'VENDU' AND p.agencyAdmin.id = :adminId AND p.updatedAt >= :since " +
           "GROUP BY YEAR(p.updatedAt), MONTH(p.updatedAt) ORDER BY YEAR(p.updatedAt), MONTH(p.updatedAt)")
    List<Object[]> getMonthlyRevenueByAgencyAdminSince(@Param("adminId") Long adminId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT p.city, p.country, COUNT(p), COALESCE(SUM(p.prixVente), 0) FROM Property p " +
           "WHERE p.statut = 'VENDU' AND p.agencyAdmin.id = :adminId AND p.city IS NOT NULL " +
           "GROUP BY p.city, p.country ORDER BY COUNT(p) DESC")
    List<Object[]> getTopCitiesBySalesByAgencyAdmin(@Param("adminId") Long adminId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p.city, p.country, COUNT(p) FROM Property p " +
           "WHERE p.isActive = true AND p.statut = 'DISPONIBLE' AND p.agencyAdmin.id = :adminId AND p.city IS NOT NULL " +
           "GROUP BY p.city, p.country ORDER BY COUNT(p) DESC")
    List<Object[]> getTopCitiesByActiveByAgencyAdmin(@Param("adminId") Long adminId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p.type, COUNT(p), p.statut FROM Property p WHERE p.isActive = true AND p.agencyAdmin.id = :adminId GROUP BY p.type, p.statut")
    List<Object[]> getTypeStatsByStatusByAgencyAdmin(@Param("adminId") Long adminId);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.isActive = true AND p.statut = 'DISPONIBLE' AND p.agencyAdmin.id = :adminId AND p.createdAt < :threshold")
    long countStagnantByAgencyAdmin(@Param("adminId") Long adminId, @Param("threshold") java.time.LocalDateTime threshold);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.statut = 'VENDU' AND p.agencyAdmin.id = :adminId AND p.updatedAt >= :start AND p.updatedAt < :end")
    long countVenduBetweenByAgencyAdmin(@Param("adminId") Long adminId, @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.statut = 'LOUE' AND p.agencyAdmin.id = :adminId AND p.updatedAt >= :start AND p.updatedAt < :end")
    long countLoueBetweenByAgencyAdmin(@Param("adminId") Long adminId, @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    /** Single property access check for an agency (visible = owns it OR it's shared with them). */
    @Query("SELECT COUNT(p) > 0 FROM Property p WHERE p.id = :propertyId AND p.isActive = true AND (" +
           "  p.ownerType IS NULL OR" +
           "  (p.ownerType = 'AGENCY_OWNED' AND p.agencyAdmin.id = :agencyAdminId) OR" +
           "  (p.ownerType = 'SUPER_ADMIN_OWNED' AND EXISTS (" +
           "    SELECT psa FROM PropertySharedAgency psa WHERE psa.property.id = :propertyId AND psa.agencyAdmin.id = :agencyAdminId" +
           "  ))" +
           ")")
    boolean isPropertyVisibleForAgency(@Param("propertyId") Long propertyId,
                                       @Param("agencyAdminId") Long agencyAdminId);

    /**
     * Finds all LOUE properties whose rental end date has passed — used by the
     * daily scheduler to reset them to DISPONIBLE.
     */
    @Query("""
        SELECT p FROM Property p
        WHERE p.statut = 'LOUE'
          AND p.rentalEndDate IS NOT NULL
          AND p.rentalEndDate < :now
        """)
    List<Property> findExpiredRentals(@Param("now") java.time.LocalDateTime now);

    // ── Rental Revenue BI ────────────────────────────────────────────────────

    /** Sum of prixLocation for all currently active LOUE properties (current MRR). */
    @Query("SELECT COALESCE(SUM(p.prixLocation), 0) FROM Property p " +
           "WHERE p.statut = 'LOUE' AND p.prixLocation IS NOT NULL")
    Double getTotalActiveRentalRevenue();

    /** Agency-scoped current rental MRR. */
    @Query("SELECT COALESCE(SUM(p.prixLocation), 0) FROM Property p " +
           "WHERE p.statut = 'LOUE' AND p.prixLocation IS NOT NULL AND p.agencyAdmin.id = :adminId")
    Double getTotalActiveRentalRevenueByAgency(@Param("adminId") Long adminId);

    /**
     * Rental revenue for a specific month: sums prixLocation of LOUE properties whose
     * contract window overlaps [startOfMonth, endOfMonth].
     * Note: only reflects currently active contracts (expired+cleared by scheduler are lost).
     */
    @Query("SELECT COALESCE(SUM(p.prixLocation), 0) FROM Property p " +
           "WHERE p.statut = 'LOUE' AND p.prixLocation IS NOT NULL " +
           "AND (p.rentalStartDate IS NULL OR p.rentalStartDate <= :endOfMonth) " +
           "AND (p.rentalEndDate IS NULL OR p.rentalEndDate >= :startOfMonth)")
    Double getRentalRevenueForMonth(@Param("startOfMonth") java.time.LocalDateTime startOfMonth,
                                    @Param("endOfMonth") java.time.LocalDateTime endOfMonth);

    /** Agency-scoped rental revenue for a specific month. */
    @Query("SELECT COALESCE(SUM(p.prixLocation), 0) FROM Property p " +
           "WHERE p.statut = 'LOUE' AND p.prixLocation IS NOT NULL AND p.agencyAdmin.id = :adminId " +
           "AND (p.rentalStartDate IS NULL OR p.rentalStartDate <= :endOfMonth) " +
           "AND (p.rentalEndDate IS NULL OR p.rentalEndDate >= :startOfMonth)")
    Double getRentalRevenueForMonthByAgency(@Param("adminId") Long adminId,
                                            @Param("startOfMonth") java.time.LocalDateTime startOfMonth,
                                            @Param("endOfMonth") java.time.LocalDateTime endOfMonth);

    /** Count LOUE properties whose contract expires within [now, future] — "expiring soon" alert. */
    @Query("SELECT COUNT(p) FROM Property p WHERE p.statut = 'LOUE' " +
           "AND p.rentalEndDate IS NOT NULL AND p.rentalEndDate BETWEEN :now AND :future")
    long countExpiringRentals(@Param("now") java.time.LocalDateTime now,
                               @Param("future") java.time.LocalDateTime future);

    /** Agency-scoped expiring count. */
    @Query("SELECT COUNT(p) FROM Property p WHERE p.statut = 'LOUE' AND p.agencyAdmin.id = :adminId " +
           "AND p.rentalEndDate IS NOT NULL AND p.rentalEndDate BETWEEN :now AND :future")
    long countExpiringRentalsByAgency(@Param("adminId") Long adminId,
                                       @Param("now") java.time.LocalDateTime now,
                                       @Param("future") java.time.LocalDateTime future);

    /** Total location properties (LOUE + DISPONIBLE with prixLocation > 0) for occupancy rate. */
    @Query("SELECT COUNT(p) FROM Property p " +
           "WHERE p.isActive = true AND p.prixLocation IS NOT NULL AND p.prixLocation > 0")
    long countAllLocationProperties();

    /** Agency-scoped location property count. */
    @Query("SELECT COUNT(p) FROM Property p WHERE p.isActive = true AND p.agencyAdmin.id = :adminId " +
           "AND p.prixLocation IS NOT NULL AND p.prixLocation > 0")
    long countAllLocationPropertiesByAgency(@Param("adminId") Long adminId);

    /** Duration breakdown for active rentals (groups by rentalDurationMonths). */
    @Query("SELECT p.rentalDurationMonths, COUNT(p) FROM Property p " +
           "WHERE p.statut = 'LOUE' AND p.rentalDurationMonths IS NOT NULL " +
           "GROUP BY p.rentalDurationMonths ORDER BY p.rentalDurationMonths")
    List<Object[]> getRentalDurationBreakdown();

    /** Agency-scoped rental duration breakdown. */
    @Query("SELECT p.rentalDurationMonths, COUNT(p) FROM Property p " +
           "WHERE p.statut = 'LOUE' AND p.agencyAdmin.id = :adminId AND p.rentalDurationMonths IS NOT NULL " +
           "GROUP BY p.rentalDurationMonths ORDER BY p.rentalDurationMonths")
    List<Object[]> getRentalDurationBreakdownByAgency(@Param("adminId") Long adminId);

    /** Agency rental revenue ranking — SUPER_ADMIN only. */
    @Query("SELECT p.agencyAdmin.id, p.agencyAdmin.nom, p.agencyAdmin.prenom, " +
           "COALESCE(SUM(p.prixLocation), 0) FROM Property p " +
           "WHERE p.statut = 'LOUE' AND p.agencyAdmin IS NOT NULL AND p.prixLocation IS NOT NULL " +
           "GROUP BY p.agencyAdmin.id, p.agencyAdmin.nom, p.agencyAdmin.prenom " +
           "ORDER BY SUM(p.prixLocation) DESC")
    List<Object[]> getAgencyRentalRevenueRanking(org.springframework.data.domain.Pageable pageable);
}