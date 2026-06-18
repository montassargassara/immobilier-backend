package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "images", indexes = {
    @Index(name = "idx_images_property_id", columnList = "property_id"),
    @Index(name = "idx_images_is_primary", columnList = "is_primary"),
    @Index(name = "idx_images_sort_order", columnList = "sort_order")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Image {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "property_id", nullable = false)
    private Long propertyId;
    
    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;
    
    @Column(name = "file_type", length = 100, nullable = false)
    private String fileType;
    
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    
    @Lob
    @Column(name = "file_data", columnDefinition = "LONGBLOB", nullable = false)
    private byte[] fileData;
    
    @Column(name = "width")
    private Integer width;
    
    @Column(name = "height")
    private Integer height;
    
    @Column(name = "alt_text", length = 500)
    private String altText;
    
    @Column(name = "title", length = 255)
    private String title;
    
    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;
    
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (sortOrder == null) sortOrder = 0;
        if (isPrimary == null) isPrimary = false;
        if (isActive == null) isActive = true;
    }
}