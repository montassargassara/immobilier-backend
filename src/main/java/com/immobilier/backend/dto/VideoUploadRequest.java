package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class VideoUploadRequest {
    private String title;
    private String description;
    private boolean isPrimary = false;
    private Integer sortOrder;
}