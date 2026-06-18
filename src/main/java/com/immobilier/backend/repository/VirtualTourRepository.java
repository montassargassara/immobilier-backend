package com.immobilier.backend.repository;

import com.immobilier.backend.entity.VirtualTour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VirtualTourRepository extends JpaRepository<VirtualTour, Long> {

    // Safe: returns list so it never throws IncorrectResultSizeDataAccessException
    @Query("SELECT t FROM VirtualTour t WHERE t.propertyId = :propertyId ORDER BY t.createdAt DESC")
    List<VirtualTour> findAllByPropertyIdDesc(@Param("propertyId") Long propertyId);

    // Kept for delete/exists checks — unique constraint makes these safe
    Optional<VirtualTour> findByPropertyId(Long propertyId);
    boolean existsByPropertyId(Long propertyId);
    void deleteByPropertyId(Long propertyId);
}
