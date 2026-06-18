package com.immobilier.backend.repository;

import com.immobilier.backend.entity.Video;
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
public interface VideoRepository extends JpaRepository<Video, Long> {
    
    // Basic find methods
    List<Video> findByPropertyId(Long propertyId);
    
    List<Video> findByPropertyIdAndIsActiveTrue(Long propertyId);
    
    List<Video> findByPropertyIdOrderBySortOrderAsc(Long propertyId);
    
    Optional<Video> findByPropertyIdAndIsPrimaryTrue(Long propertyId);
    
    List<Video> findByPropertyIdAndIsActiveTrueOrderBySortOrderAsc(Long propertyId);
    
    // Paginated version
    Page<Video> findByPropertyId(Long propertyId, Pageable pageable);
    
    // Custom queries for active videos
    @Query("SELECT v FROM Video v WHERE v.propertyId = :propertyId AND v.isActive = true ORDER BY v.sortOrder ASC")
    List<Video> findActiveVideosByPropertyId(@Param("propertyId") Long propertyId);

        @Query("SELECT new com.immobilier.backend.dto.VideoDTO(v.id, v.propertyId, v.fileName, v.fileType, v.fileSize, " +
            "v.duration, v.width, v.height, v.title, v.description, v.isPrimary, v.sortOrder, v.createdAt) " +
            "FROM Video v WHERE v.id = :id AND v.isActive = true")
        Optional<com.immobilier.backend.dto.VideoDTO> findActiveVideoInfoById(@Param("id") Long id);

        @Query("SELECT new com.immobilier.backend.dto.VideoDTO(v.id, v.propertyId, v.fileName, v.fileType, v.fileSize, " +
            "v.duration, v.width, v.height, v.title, v.description, v.isPrimary, v.sortOrder, v.createdAt) " +
            "FROM Video v WHERE v.propertyId = :propertyId AND v.isActive = true ORDER BY v.sortOrder ASC")
        List<com.immobilier.backend.dto.VideoDTO> findActiveVideosInfoByPropertyId(@Param("propertyId") Long propertyId);
    
    // Find primary video
    @Query("SELECT v FROM Video v WHERE v.propertyId = :propertyId AND v.isPrimary = true AND v.isActive = true")
    Optional<Video> findActivePrimaryVideoByPropertyId(@Param("propertyId") Long propertyId);

        @Query("SELECT new com.immobilier.backend.dto.VideoDTO(v.id, v.propertyId, v.fileName, v.fileType, v.fileSize, " +
            "v.duration, v.width, v.height, v.title, v.description, v.isPrimary, v.sortOrder, v.createdAt) " +
            "FROM Video v WHERE v.propertyId = :propertyId AND v.isPrimary = true AND v.isActive = true")
        Optional<com.immobilier.backend.dto.VideoDTO> findActivePrimaryVideoInfoByPropertyId(@Param("propertyId") Long propertyId);
    
    // Count methods
    @Query("SELECT COUNT(v) FROM Video v WHERE v.propertyId = :propertyId AND v.isActive = true")
    long countActiveVideosByPropertyId(@Param("propertyId") Long propertyId);
    
    // Alternative count method using method naming convention
    long countByPropertyIdAndIsActiveTrue(Long propertyId);
    
    // Update methods
    @Modifying
    @Transactional
    @Query("UPDATE Video v SET v.isPrimary = false WHERE v.propertyId = :propertyId")
    void resetPrimaryFlag(@Param("propertyId") Long propertyId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Video v SET v.isActive = false WHERE v.propertyId = :propertyId")
    void softDeleteByPropertyId(@Param("propertyId") Long propertyId);
    
    // Delete method
    @Modifying
    @Transactional
    void deleteByPropertyId(Long propertyId);
}