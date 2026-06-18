package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "videos", indexes = {
    @Index(name = "idx_videos_property_id", columnList = "property_id"),
    @Index(name = "idx_videos_is_primary", columnList = "is_primary"),
    @Index(name = "idx_videos_sort_order", columnList = "sort_order")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Video {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "property_id", nullable = false)
    private Long propertyId;
    
    @Column(name = "file_name", length = 255)
    private String fileName;

    // Filesystem path (set for virtual-tour source videos; null for BLOB-stored videos)
    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Lob
    @Column(name = "file_data", columnDefinition = "LONGBLOB")
    private byte[] fileData;
    
    @Column(name = "duration")
    private Integer duration; // Duration in seconds
    
    @Column(name = "width")
    private Integer width;
    
    @Column(name = "height")
    private Integer height;
    
    @Column(name = "thumbnail", columnDefinition = "LONGBLOB")
    private byte[] thumbnail; // Video thumbnail
    
    @Column(name = "title", length = 255)
    private String title;
    
    @Column(name = "description", length = 1000)
    private String description;
    
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