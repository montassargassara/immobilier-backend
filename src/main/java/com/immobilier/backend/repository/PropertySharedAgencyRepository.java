package com.immobilier.backend.repository;

import com.immobilier.backend.entity.PropertySharedAgency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertySharedAgencyRepository extends JpaRepository<PropertySharedAgency, Long> {

    List<PropertySharedAgency> findByPropertyId(Long propertyId);

    List<PropertySharedAgency> findByAgencyAdminId(Long agencyAdminId);

    boolean existsByPropertyIdAndAgencyAdminId(Long propertyId, Long agencyAdminId);

    @Modifying
    @Query("DELETE FROM PropertySharedAgency psa WHERE psa.property.id = :propertyId AND psa.agencyAdmin.id = :agencyAdminId")
    void deleteByPropertyIdAndAgencyAdminId(
            @Param("propertyId") Long propertyId,
            @Param("agencyAdminId") Long agencyAdminId);

    @Modifying
    @Query("DELETE FROM PropertySharedAgency psa WHERE psa.property.id = :propertyId")
    void deleteAllByPropertyId(@Param("propertyId") Long propertyId);

    @Query("SELECT psa.agencyAdmin.id FROM PropertySharedAgency psa WHERE psa.property.id = :propertyId")
    List<Long> findAgencyAdminIdsByPropertyId(@Param("propertyId") Long propertyId);

    @Query("SELECT psa.property.id FROM PropertySharedAgency psa WHERE psa.agencyAdmin.id = :agencyAdminId")
    List<Long> findPropertyIdsByAgencyAdminId(@Param("agencyAdminId") Long agencyAdminId);
}
