package com.immobilier.backend.dto;

import com.immobilier.backend.entity.MapAnythingJob;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MapAnythingJobDTO {

    private Long id;
    private Long propertyId;

    /** PENDING | PROCESSING | AWAITING_VALIDATION | ACCEPTED | REJECTED | FAILED */
    private String status;
    private Integer processingProgress;
    private String currentStep;
    private String errorMessage;

    /** True when a preview file is ready for admin inspection (AWAITING_VALIDATION or ACCEPTED). */
    private boolean glbAvailable;

    /** Permanent public URL — set ONLY after ACCEPTED (model3d_id populated). */
    private String glbUrl;

    /** Set once the GLB is published as a Model3D record (status = ACCEPTED). */
    private Long model3dId;

    // ── Stats (populated at AWAITING_VALIDATION) ──────────────────────────────
    private Long glbFileSize;
    private Long vertexCount;
    private Integer meshCount;
    private Long generationTimeMs;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MapAnythingJobDTO from(MapAnythingJob job, String baseUrl) {
        MapAnythingJobDTO dto = new MapAnythingJobDTO();
        dto.setId(job.getId());
        dto.setPropertyId(job.getPropertyId());
        dto.setStatus(job.getStatus());
        dto.setProcessingProgress(job.getProcessingProgress() != null ? job.getProcessingProgress() : 0);
        dto.setCurrentStep(job.getCurrentStep());
        dto.setErrorMessage(job.getErrorMessage());
        dto.setModel3dId(job.getModel3dId());
        dto.setGlbFileSize(job.getGlbFileSize());
        dto.setVertexCount(job.getVertexCount());
        dto.setMeshCount(job.getMeshCount());
        dto.setGenerationTimeMs(job.getGenerationTimeMs());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setUpdatedAt(job.getUpdatedAt());

        // Preview available for admin when GLB is ready but not yet published (or already accepted)
        if ("AWAITING_VALIDATION".equals(job.getStatus()) || "ACCEPTED".equals(job.getStatus())) {
            dto.setGlbAvailable(true);
        }
        // Public permanent URL — only after ACCEPTED (served via /api/models/public/{id})
        if ("ACCEPTED".equals(job.getStatus()) && job.getModel3dId() != null) {
            dto.setGlbUrl(baseUrl + "/api/models/public/" + job.getModel3dId());
        }
        return dto;
    }

    public static MapAnythingJobDTO notCreated() {
        MapAnythingJobDTO dto = new MapAnythingJobDTO();
        dto.setStatus("NOT_CREATED");
        dto.setProcessingProgress(0);
        return dto;
    }
}
