package com.immobilier.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class ImageDTO {
    private Long id;
    private Long propertyId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String altText;
    private String title;
    private Boolean isPrimary;
    private Integer sortOrder;
    private String url;
    private LocalDateTime createdAt;

    public ImageDTO(Long id, Long propertyId, String fileName, String fileType, Long fileSize,
                    Integer width, Integer height, String altText, String title,
                    Boolean isPrimary, Integer sortOrder, LocalDateTime createdAt) {
        this.id = id;
        this.propertyId = propertyId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.width = width;
        this.height = height;
        this.altText = altText;
        this.title = title;
        this.isPrimary = isPrimary;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }
}