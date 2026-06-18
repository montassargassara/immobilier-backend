package com.immobilier.backend.dto;

import com.immobilier.backend.entity.VirtualTourScene;
import lombok.Data;

@Data
public class VirtualTourSceneDTO {
    private Long id;
    private Long tourId;
    private Integer sceneIndex;
    private String sceneName;
    private String imageUrl;
    private String thumbnailUrl;
    private Double timestampSeconds;
    private Boolean isDefault;

    public static VirtualTourSceneDTO from(VirtualTourScene scene, Long tourId, String apiBase) {
        VirtualTourSceneDTO dto = new VirtualTourSceneDTO();
        dto.setId(scene.getId());
        dto.setTourId(scene.getTourId());
        dto.setSceneIndex(scene.getSceneIndex());
        dto.setSceneName(scene.getSceneName());
        dto.setTimestampSeconds(scene.getTimestampSeconds());
        dto.setIsDefault(scene.getIsDefault());

        String base = apiBase + "/api/virtual-tour/public/scene-image/" + tourId + "/";
        if (scene.getImageFilename() != null) {
            dto.setImageUrl(base + scene.getImageFilename());
        }
        if (scene.getThumbnailFilename() != null) {
            dto.setThumbnailUrl(base + scene.getThumbnailFilename());
        }
        return dto;
    }
}
