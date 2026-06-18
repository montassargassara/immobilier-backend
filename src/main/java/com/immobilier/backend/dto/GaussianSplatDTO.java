package com.immobilier.backend.dto;

import com.immobilier.backend.entity.GaussianSplat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GaussianSplatDTO {

    private Long id;
    private Long propertyId;
    private String status;
    private Integer processingProgress;
    private String currentStep;
    private String errorMessage;
    private Integer iterations;
    private Integer sceneCount;
    private String plyUrl;
    /** True when a preview file is ready for admin inspection (AWAITING_VALIDATION or ACCEPTED). */
    private boolean previewAvailable;
    /** Format of the preview file: 'ply' | 'ksplat' | 'splat'. Null when no preview exists. */
    private String previewFormat;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GaussianSplatDTO from(GaussianSplat g, String baseUrl) {
        GaussianSplatDTO dto = new GaussianSplatDTO();
        dto.setId(g.getId());
        dto.setPropertyId(g.getPropertyId());
        dto.setStatus(g.getStatus());
        dto.setProcessingProgress(g.getProcessingProgress() != null ? g.getProcessingProgress() : 0);
        dto.setCurrentStep(g.getCurrentStep());
        dto.setErrorMessage(g.getErrorMessage());
        dto.setIterations(g.getIterations());
        dto.setSceneCount(g.getSceneCount() != null ? g.getSceneCount() : 0);
        dto.setCreatedAt(g.getCreatedAt());
        dto.setUpdatedAt(g.getUpdatedAt());
        // Legacy public PLY URL — only for backward-compat COMPLETED rows
        if ("COMPLETED".equals(g.getStatus()) && g.getId() != null) {
            dto.setPlyUrl(baseUrl + "/api/gaussian-splat/file/" + g.getId() + "/point_cloud.ply");
        }
        // Preview info for admin validation workflow
        if (g.getPreviewFilePath() != null
                && ("AWAITING_VALIDATION".equals(g.getStatus()) || "ACCEPTED".equals(g.getStatus()))) {
            dto.setPreviewAvailable(true);
            dto.setPreviewFormat(g.getPreviewFormat());
        }
        return dto;
    }

    public static GaussianSplatDTO notCreated() {
        GaussianSplatDTO dto = new GaussianSplatDTO();
        dto.setStatus("NOT_CREATED");
        dto.setProcessingProgress(0);
        return dto;
    }
}
