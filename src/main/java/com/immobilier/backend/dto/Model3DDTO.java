package com.immobilier.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class Model3DDTO {
    private Long id;
    private Long propertyId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String format;
    private Integer polygonCount;
    private String description;
    private String url;
    private LocalDateTime createdAt;

    public Model3DDTO(Long id, Long propertyId, String fileName, String fileType, Long fileSize,
                      String format, Integer polygonCount, String description, LocalDateTime createdAt) {
        this.id = id;
        this.propertyId = propertyId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.format = format;
        this.polygonCount = polygonCount;
        this.description = description;
        this.createdAt = createdAt;
    }
}