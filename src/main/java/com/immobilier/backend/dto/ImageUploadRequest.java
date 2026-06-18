package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class ImageUploadRequest {
    private String altText;
    private String title;
    private boolean isPrimary = false;
    private Integer sortOrder;
}