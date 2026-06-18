package com.immobilier.backend.dto;

import com.immobilier.backend.entity.VirtualTour;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class VirtualTourDTO {
    private Long id;
    private Long propertyId;
    private String status;
    private Integer sceneCount;
    private Integer processingProgress;
    private String errorMessage;
    private Boolean is360;
    private Double videoDurationSeconds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<VirtualTourSceneDTO> scenes;
    private Long sourceVideoId;
    private Long model3dId;

    /** Returned when no tour record exists yet for a property. Frontend checks status === "NOT_CREATED". */
    public static VirtualTourDTO notCreated() {
        VirtualTourDTO dto = new VirtualTourDTO();
        dto.setStatus("NOT_CREATED");
        dto.setScenes(List.of());
        dto.setSceneCount(0);
        dto.setProcessingProgress(0);
        return dto;
    }

    public static VirtualTourDTO from(VirtualTour tour, List<VirtualTourSceneDTO> scenes) {
        VirtualTourDTO dto = new VirtualTourDTO();
        dto.setId(tour.getId());
        dto.setPropertyId(tour.getPropertyId());
        dto.setStatus(tour.getStatus());
        dto.setSceneCount(tour.getSceneCount());
        dto.setProcessingProgress(tour.getProcessingProgress());
        dto.setErrorMessage(tour.getErrorMessage());
        dto.setIs360(tour.getIs360());
        dto.setVideoDurationSeconds(tour.getVideoDurationSeconds());
        dto.setCreatedAt(tour.getCreatedAt());
        dto.setUpdatedAt(tour.getUpdatedAt());
        dto.setScenes(scenes);
        dto.setSourceVideoId(tour.getSourceVideoId());
        dto.setModel3dId(tour.getModel3dId());
        return dto;
    }
}
