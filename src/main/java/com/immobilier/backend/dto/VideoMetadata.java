package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class VideoMetadata {
    private Integer duration;
    private Integer width;
    private Integer height;
    private byte[] thumbnail;
}