package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "virtual_tour_scenes", indexes = {
    @Index(name = "idx_vts_tour_id", columnList = "tour_id"),
    @Index(name = "idx_vts_scene_index", columnList = "scene_index")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VirtualTourScene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "scene_index", nullable = false)
    private Integer sceneIndex;

    @Column(name = "scene_name", length = 255)
    private String sceneName;

    @Column(name = "image_filename", length = 255)
    private String imageFilename;

    @Column(name = "thumbnail_filename", length = 255)
    private String thumbnailFilename;

    @Column(name = "timestamp_seconds")
    private Double timestampSeconds;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @PrePersist
    void defaults() {
        if (isDefault == null) isDefault = false;
    }
}
