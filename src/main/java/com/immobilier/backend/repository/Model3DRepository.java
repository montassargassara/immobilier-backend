package com.immobilier.backend.repository;

import com.immobilier.backend.entity.Model3D;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface Model3DRepository extends JpaRepository<Model3D, Long> {
    
    // Add this method for finding all models by propertyId
    List<Model3D> findByPropertyId(Long propertyId);
    
    List<Model3D> findByPropertyIdAndIsActiveTrue(Long propertyId);
    
    Optional<Model3D> findByPropertyIdAndFormat(Long propertyId, String format);
    
    @Query("SELECT m FROM Model3D m WHERE m.propertyId = :propertyId AND m.isActive = true")
    Optional<Model3D> findActiveModelByPropertyId(@Param("propertyId") Long propertyId);

        @Query("SELECT new com.immobilier.backend.dto.Model3DDTO(m.id, m.propertyId, m.fileName, m.fileType, m.fileSize, m.format, m.polygonCount, m.description, m.createdAt) " +
            "FROM Model3D m WHERE m.id = :id AND m.isActive = true")
        Optional<com.immobilier.backend.dto.Model3DDTO> findActiveModelInfoById(@Param("id") Long id);

        @Query("SELECT new com.immobilier.backend.dto.Model3DDTO(m.id, m.propertyId, m.fileName, m.fileType, m.fileSize, m.format, m.polygonCount, m.description, m.createdAt) " +
            "FROM Model3D m WHERE m.propertyId = :propertyId AND m.isActive = true " +
            "ORDER BY m.createdAt DESC")
        List<com.immobilier.backend.dto.Model3DDTO> findActiveModelsInfoByPropertyId(@Param("propertyId") Long propertyId);

    @Modifying
    @Transactional
    @Query("UPDATE Model3D m SET m.isActive = false WHERE m.propertyId = :propertyId AND m.fileType = 'virtual_tour'")
    void archiveVirtualTourModelsByPropertyId(@Param("propertyId") Long propertyId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Model3D m SET m.isActive = false WHERE m.propertyId = :propertyId")
    void softDeleteByPropertyId(@Param("propertyId") Long propertyId);
    
    long countByPropertyIdAndIsActiveTrue(Long propertyId);
}