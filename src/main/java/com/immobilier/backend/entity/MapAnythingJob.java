package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "mapanything_jobs", indexes = {
    @Index(name = "idx_ma_property_id", columnList = "property_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapAnythingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id")
    private Long propertyId;

    /** PENDING | PROCESSING | ACCEPTED | FAILED — VARCHAR, never MySQL ENUM. */
    @Column(name = "status", length = 50, nullable = false)
    private String status;

    @Column(name = "processing_progress")
    private Integer processingProgress = 0;

    @Column(name = "current_step", columnDefinition = "TEXT")
    private String currentStep;

    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(name = "glb_file_path", length = 1000)
    private String glbFilePath;

    @Column(name = "glb_file_size")
    private Long glbFileSize;

    @Column(name = "source_video_path", length = 1000)
    private String sourceVideoPath;

    @Column(name = "work_dir", length = 1000)
    private String workDir;

    /** Set once the GLB is published as a Model3D record. */
    @Column(name = "model3d_id")
    private Long model3dId;

    /** Total vertex count across all meshes (populated at AWAITING_VALIDATION). */
    @Column(name = "vertex_count")
    private Long vertexCount;

    /** Number of meshes in the GLB (populated at AWAITING_VALIDATION). */
    @Column(name = "mesh_count")
    private Integer meshCount;

    /** Pipeline wall-clock duration in milliseconds from start to AWAITING_VALIDATION. */
    @Column(name = "generation_time_ms")
    private Long generationTimeMs;

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
    }
}
