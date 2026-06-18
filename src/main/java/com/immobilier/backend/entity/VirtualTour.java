package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "virtual_tours", indexes = {
    @Index(name = "idx_vt_property_id", columnList = "property_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VirtualTour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = true)
    private Long propertyId;

    // PENDING | PROCESSING | COMPLETED | FAILED
    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "source_video_path", length = 500)
    private String sourceVideoPath;

    @Column(name = "tour_dir", length = 500)
    private String tourDir;

    @Column(name = "scene_count")
    private Integer sceneCount = 0;

    @Column(name = "processing_progress")
    private Integer processingProgress = 0;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "is_360")
    private Boolean is360 = false;

    @Column(name = "video_duration_seconds")
    private Double videoDurationSeconds;

    // FK references to the shared tables (nullable — set after creation)
    @Column(name = "source_video_id")
    private Long sourceVideoId;

    @Column(name = "model3d_id")
    private Long model3dId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void defaults() {
        if (status == null) status = "PENDING";
        if (processingProgress == null) processingProgress = 0;
        if (sceneCount == null) sceneCount = 0;
        if (is360 == null) is360 = false;
    }
}
