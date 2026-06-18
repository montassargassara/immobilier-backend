package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "gaussian_splats", indexes = {
    @Index(name = "idx_gs_property_id", columnList = "property_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GaussianSplat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = true)
    private Long propertyId;

    // PENDING | PROCESSING | COMPLETED | FAILED
    @Column(name = "status", length = 50, nullable = false)
    private String status;

    // Windows absolute paths can exceed 260 chars — use VARCHAR(1000) to be safe
    @Column(name = "source_video_path", length = 1000)
    private String sourceVideoPath;

    @Column(name = "work_dir", length = 1000)
    private String workDir;

    @Column(name = "ply_file_path", length = 1000)
    private String plyFilePath;

    // Path to the file served by the secure /preview endpoint (ply or ksplat)
    @Column(name = "preview_file_path", length = 1000)
    private String previewFilePath;

    // Format of the preview file: 'ply' | 'ksplat' | 'splat'
    @Column(name = "preview_format", length = 20)
    private String previewFormat;

    @Column(name = "processing_progress")
    private Integer processingProgress = 0;

    // TEXT (no length limit) — pipeline step descriptions include command output
    @Column(name = "current_step", columnDefinition = "TEXT")
    private String currentStep;

    // LONGTEXT — Python/FFmpeg error output can be very large
    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(name = "iterations")
    private Integer iterations = 30000;

    @Column(name = "scene_count")
    private Integer sceneCount = 0;

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
        if (iterations == null) iterations = 30000;
        if (sceneCount == null) sceneCount = 0;
    }
}
