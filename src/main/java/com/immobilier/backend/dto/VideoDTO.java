package com.immobilier.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class VideoDTO {
    private Long id;
    private Long propertyId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer duration;
    private Integer width;
    private Integer height;
    private String title;
    private String description;
    private Boolean isPrimary;
    private Integer sortOrder;
    private String url;
    private String thumbnailUrl;
    private LocalDateTime createdAt;

    public VideoDTO(Long id, Long propertyId, String fileName, String fileType, Long fileSize,
                    Integer duration, Integer width, Integer height, String title,
                    String description, Boolean isPrimary, Integer sortOrder, LocalDateTime createdAt) {
        this.id = id;
        this.propertyId = propertyId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.duration = duration;
        this.width = width;
        this.height = height;
        this.title = title;
        this.description = description;
        this.isPrimary = isPrimary;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }
}