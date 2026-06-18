package com.immobilier.backend.repository;

import com.immobilier.backend.entity.GaussianSplat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface GaussianSplatRepository extends JpaRepository<GaussianSplat, Long> {

    @Query("SELECT g FROM GaussianSplat g WHERE g.propertyId = :propertyId ORDER BY g.createdAt DESC")
    List<GaussianSplat> findAllByPropertyIdDesc(@Param("propertyId") Long propertyId);

    @Query("SELECT g FROM GaussianSplat g WHERE g.propertyId = :propertyId AND g.status = 'COMPLETED' ORDER BY g.createdAt DESC")
    Optional<GaussianSplat> findLatestCompletedForProperty(@Param("propertyId") Long propertyId);
}
