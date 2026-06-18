package com.immobilier.backend.repository;

import com.immobilier.backend.entity.Image;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {
    
    // Add this method for finding all images by propertyId without pagination
    List<Image> findByPropertyId(Long propertyId);
    
    // Add this method for finding active images only
    List<Image> findByPropertyIdAndIsActiveTrue(Long propertyId);
    
    List<Image> findByPropertyIdOrderBySortOrderAsc(Long propertyId);
    
    Optional<Image> findByPropertyIdAndIsPrimaryTrue(Long propertyId);
    
    List<Image> findByPropertyIdAndIsActiveTrueOrderBySortOrderAsc(Long propertyId);
    
    Page<Image> findByPropertyId(Long propertyId, Pageable pageable);
    
    @Query("SELECT i FROM Image i WHERE i.propertyId = :propertyId AND i.isActive = true ORDER BY i.sortOrder ASC")
    List<Image> findActiveImagesByPropertyId(@Param("propertyId") Long propertyId);

        @Query("SELECT new com.immobilier.backend.dto.ImageDTO(i.id, i.propertyId, i.fileName, i.fileType, i.fileSize, " +
            "i.width, i.height, i.altText, i.title, i.isPrimary, i.sortOrder, i.createdAt) " +
            "FROM Image i WHERE i.id = :id AND i.isActive = true")
        Optional<com.immobilier.backend.dto.ImageDTO> findActiveImageInfoById(@Param("id") Long id);

        @Query("SELECT new com.immobilier.backend.dto.ImageDTO(i.id, i.propertyId, i.fileName, i.fileType, i.fileSize, " +
            "i.width, i.height, i.altText, i.title, i.isPrimary, i.sortOrder, i.createdAt) " +
            "FROM Image i WHERE i.propertyId = :propertyId AND i.isActive = true ORDER BY i.sortOrder ASC")
        List<com.immobilier.backend.dto.ImageDTO> findActiveImagesInfoByPropertyId(@Param("propertyId") Long propertyId);
    
    @Query("SELECT i FROM Image i WHERE i.propertyId = :propertyId AND i.isPrimary = true AND i.isActive = true")
    Optional<Image> findActivePrimaryImage(@Param("propertyId") Long propertyId);

        @Query("SELECT new com.immobilier.backend.dto.ImageDTO(i.id, i.propertyId, i.fileName, i.fileType, i.fileSize, " +
            "i.width, i.height, i.altText, i.title, i.isPrimary, i.sortOrder, i.createdAt) " +
            "FROM Image i WHERE i.propertyId = :propertyId AND i.isPrimary = true AND i.isActive = true")
        Optional<com.immobilier.backend.dto.ImageDTO> findActivePrimaryImageInfoByPropertyId(@Param("propertyId") Long propertyId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Image i SET i.isPrimary = false WHERE i.propertyId = :propertyId")
    void resetPrimaryFlag(@Param("propertyId") Long propertyId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Image i SET i.sortOrder = i.sortOrder - 1 WHERE i.propertyId = :propertyId AND i.sortOrder > :sortOrder")
    void decrementSortOrder(@Param("propertyId") Long propertyId, @Param("sortOrder") Integer sortOrder);
    
    @Query("SELECT COUNT(i) FROM Image i WHERE i.propertyId = :propertyId AND i.isActive = true")
    long countActiveImagesByPropertyId(@Param("propertyId") Long propertyId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Image i SET i.isActive = false WHERE i.propertyId = :propertyId")
    void softDeleteByPropertyId(@Param("propertyId") Long propertyId);
}