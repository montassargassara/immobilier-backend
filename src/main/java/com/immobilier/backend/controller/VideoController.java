package com.immobilier.backend.controller;

import com.immobilier.backend.dto.VideoDTO;
import com.immobilier.backend.dto.VideoUploadRequest;
import com.immobilier.backend.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    // Handle OPTIONS requests for preflight
    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        return ResponseEntity.ok().build();
    }

    // ==================== ROUTES PUBLIQUES ====================
    
    @GetMapping("/public/{id}")
    public ResponseEntity<byte[]> getVideoPublic(@PathVariable Long id) {
        try {
            log.info("🎬 Public video request for ID: {}", id);
            
            byte[] videoData = videoService.getVideoData(id);
            VideoDTO videoInfo = videoService.getVideoInfoById(id);
            
            if (videoData == null || videoData.length == 0) {
                log.warn("⚠️ No data for video ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(videoInfo.getFileType()));
            headers.setContentLength(videoInfo.getFileSize());
            headers.setCacheControl("public, max-age=86400");
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            
            log.info("✅ Serving video ID: {}, size: {} bytes", id, videoData.length);
            
            return new ResponseEntity<>(videoData, headers, HttpStatus.OK);
        } catch (RuntimeException e) {
            log.error("Video not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/public/{id}/thumbnail")
    public ResponseEntity<byte[]> getVideoThumbnailPublic(@PathVariable Long id) {
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

    @GetMapping("/public/{id}/info")
    public ResponseEntity<VideoDTO> getVideoInfoPublic(@PathVariable Long id) {
        VideoDTO video = videoService.getVideoInfoById(id);
        return video != null ? ResponseEntity.ok(video) : ResponseEntity.notFound().build();
    }

    // ==================== ROUTES PROTÉGÉES ====================
    
    @GetMapping("/property/{propertyId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<VideoDTO>> getVideosByProperty(@PathVariable Long propertyId) {
        log.info("🔒 Récupération des vidéos pour la propriété ID: {}", propertyId);
        return ResponseEntity.ok(videoService.getVideosInfoByPropertyId(propertyId));
    }

    @GetMapping("/property/{propertyId}/primary")
    @PreAuthorize("hasAnyRole('CLIENT', 'COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<VideoDTO> getPrimaryVideo(@PathVariable Long propertyId) {
        log.info("🔒 Récupération de la vidéo principale pour la propriété ID: {}", propertyId);
        VideoDTO video = videoService.getPrimaryVideo(propertyId);
        return video != null ? ResponseEntity.ok(video) : ResponseEntity.notFound().build();
    }

    @PostMapping(value = "/property/{propertyId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<VideoDTO> uploadVideo(
            @PathVariable Long propertyId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isPrimary", defaultValue = "false") boolean isPrimary) throws IOException {
        
        log.info("🎥 Upload de vidéo pour la propriété ID: {}", propertyId);
        
        VideoUploadRequest request = new VideoUploadRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setPrimary(isPrimary);
        
        VideoDTO video = videoService.uploadVideo(propertyId, file, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(video);
    }

    @DeleteMapping("/{videoId}")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<String> deleteVideo(
            @PathVariable Long videoId,
            @RequestParam Long propertyId) {
        log.info("🗑️ Suppression de la vidéo ID: {} pour la propriété ID: {}", videoId, propertyId);
        videoService.deleteVideo(videoId, propertyId);
        return ResponseEntity.ok("Vidéo supprimée avec succès");
    }

    @DeleteMapping("/property/{propertyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<String> deleteAllVideos(@PathVariable Long propertyId) {
        log.info("🗑️ Suppression de toutes les vidéos pour la propriété ID: {}", propertyId);
        videoService.deleteAllVideosByPropertyId(propertyId);
        return ResponseEntity.ok("Toutes les vidéos ont été supprimées avec succès");
    }
}