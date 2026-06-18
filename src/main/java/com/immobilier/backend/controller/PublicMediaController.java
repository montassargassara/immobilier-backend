package com.immobilier.backend.controller;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicMediaController {

    private final PropertyService propertyService;
    private final ImageService imageService;
    private final Model3DService model3DService;
    private final VideoService videoService;

    // Property endpoints
    @GetMapping("/properties")
    public ResponseEntity<List<PropertyListDTO>> getAllProperties() {
        return ResponseEntity.ok(propertyService.getAllPropertiesList());
    }

    @GetMapping("/properties/{id}")
    public ResponseEntity<PropertyDTO> getPropertyById(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getPropertyById(id));
    }

    @GetMapping("/properties/{id}/medias")
    public ResponseEntity<List<PropertyMediaDTO>> getPropertyMedias(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getPropertyMediaLight(id));
    }

    // Image endpoints
    @GetMapping("/images/{id}")
    @Cacheable(value = "imageData", key = "#id")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        try {
            byte[] imageData = imageService.getImageData(id);
            ImageDTO imageInfo = imageService.getImageInfoById(id);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(imageInfo.getFileType()));
            headers.setContentLength(imageInfo.getFileSize());
            headers.setCacheControl("public, max-age=86400");
            
            return new ResponseEntity<>(imageData, headers, HttpStatus.OK);
        } catch (RuntimeException e) {
            log.error("Image not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/images/{id}/info")
    public ResponseEntity<ImageDTO> getImageInfo(@PathVariable Long id) {
        ImageDTO image = imageService.getImageInfoById(id);
        return image != null ? ResponseEntity.ok(image) : ResponseEntity.notFound().build();
    }

    // Video endpoints
    @GetMapping("/videos/{id}")
    @Cacheable(value = "videoData", key = "#id")
    public ResponseEntity<byte[]> getVideo(@PathVariable Long id) {
        try {
            byte[] videoData = videoService.getVideoData(id);
            VideoDTO videoInfo = videoService.getVideoInfoById(id);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(videoInfo.getFileType()));
            headers.setContentLength(videoInfo.getFileSize());
            headers.setCacheControl("public, max-age=86400");
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            
            return new ResponseEntity<>(videoData, headers, HttpStatus.OK);
        } catch (RuntimeException e) {
            log.error("Video not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/videos/{id}/thumbnail")
    @Cacheable(value = "videoThumbnail", key = "#id")
    public ResponseEntity<byte[]> getVideoThumbnail(@PathVariable Long id) {
        try {
            byte[] thumbnail = videoService.getVideoThumbnail(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setCacheControl("public, max-age=86400");
            
            return new ResponseEntity<>(thumbnail, headers, HttpStatus.OK);
        } catch (RuntimeException e) {
            log.error("Video thumbnail not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/videos/{id}/info")
    public ResponseEntity<VideoDTO> getVideoInfo(@PathVariable Long id) {
        VideoDTO video = videoService.getVideoInfoById(id);
        return video != null ? ResponseEntity.ok(video) : ResponseEntity.notFound().build();
    }

    // 3D Model endpoints
    @GetMapping("/models/{id}")
    @Cacheable(value = "modelData", key = "#id")
    public ResponseEntity<byte[]> getModel(@PathVariable Long id) {
        try {
            byte[] modelData = model3DService.getModel3DData(id);
            Model3DDTO modelInfo = model3DService.getModel3DInfoById(id);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(modelInfo.getFileType()));
            headers.setContentLength(modelInfo.getFileSize());
            headers.setCacheControl("public, max-age=86400");
            headers.setContentDispositionFormData("inline", modelInfo.getFileName());
            
            return new ResponseEntity<>(modelData, headers, HttpStatus.OK);
        } catch (RuntimeException e) {
            log.error("3D model not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/models/{id}/info")
    public ResponseEntity<Model3DDTO> getModelInfo(@PathVariable Long id) {
        Model3DDTO model = model3DService.getModel3DInfoById(id);
        return model != null ? ResponseEntity.ok(model) : ResponseEntity.notFound().build();
    }
}