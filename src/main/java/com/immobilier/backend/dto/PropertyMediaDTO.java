package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class PropertyMediaDTO {
    private Long id;
    private String type; // IMAGE, VIDEO, MODEL_3D
    private String url;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer sortOrder;
    private Boolean isPrimary;
    private Long propertyId;
}