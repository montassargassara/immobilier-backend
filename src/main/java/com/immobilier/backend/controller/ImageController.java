package com.immobilier.backend.controller;

import com.immobilier.backend.dto.ImageDTO;
import com.immobilier.backend.dto.ImageUploadRequest;
import com.immobilier.backend.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    // ==================== ROUTES PUBLIQUES ====================
    // IMPORTANT: This endpoint MUST be public, no @PreAuthorize annotation
    @GetMapping("/public/{id}")
    public ResponseEntity<byte[]> getImagePublic(@PathVariable Long id) {
        try {
            log.info("📸 Public image request for ID: {}", id);
            
            byte[] imageData = imageService.getImageData(id);
            ImageDTO imageInfo = imageService.getImageInfoById(id);
            
            if (imageData == null || imageData.length == 0) {
                log.warn("⚠️ No data for image ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                imageInfo.getFileType() != null ? imageInfo.getFileType() : "image/jpeg"));
            headers.setContentLength(imageData.length);
            headers.setCacheControl("public, max-age=86400");
            
            log.info("✅ Serving public image ID: {}, size: {} bytes", id, imageData.length);
            
            return new ResponseEntity<>(imageData, headers, HttpStatus.OK);
            
        } catch (RuntimeException e) {
            log.error("❌ Image not found ID {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error serving image ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/public/{id}/info")
    public ResponseEntity<ImageDTO> getImageInfoPublic(@PathVariable Long id) {
        try {
            ImageDTO image = imageService.getImageInfoById(id);
            return image != null ? ResponseEntity.ok(image) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error getting image info: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== ROUTES PROTÉGÉES ====================
    
    @GetMapping("/property/{propertyId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<ImageDTO>> getImagesByProperty(@PathVariable Long propertyId) {
        log.info("🔒 Récupération des images pour la propriété ID: {}", propertyId);
        return ResponseEntity.ok(imageService.getImagesInfoByPropertyId(propertyId));
    }

    @GetMapping("/property/{propertyId}/primary")
    @PreAuthorize("hasAnyRole('CLIENT', 'COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ImageDTO> getPrimaryImage(@PathVariable Long propertyId) {
        log.info("🔒 Récupération de l'image principale pour la propriété ID: {}", propertyId);
        ImageDTO image = imageService.getPrimaryImage(propertyId);
        return image != null ? ResponseEntity.ok(image) : ResponseEntity.notFound().build();
    }

    @PostMapping(value = "/property/{propertyId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ImageDTO> uploadImage(
            @PathVariable Long propertyId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "altText", required = false) String altText,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "isPrimary", defaultValue = "false") boolean isPrimary) throws IOException {
        
        log.info("📸 Upload d'image pour la propriété ID: {}", propertyId);
        
        ImageUploadRequest request = new ImageUploadRequest();
        request.setAltText(altText);
        request.setTitle(title);
        request.setPrimary(isPrimary);
        
        ImageDTO image = imageService.uploadImage(propertyId, file, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(image);
    }

    @PostMapping(value = "/property/{propertyId}/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<ImageDTO>> uploadMultipleImages(
            @PathVariable Long propertyId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "setAsMain", defaultValue = "false") boolean setAsMain) throws IOException {
        
        log.info("📸 Upload de {} images pour la propriété ID: {}", files.length, propertyId);
        
        List<ImageDTO> results = new java.util.ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            ImageUploadRequest request = new ImageUploadRequest();
            // Set the first image as primary if setAsMain is true and it's the first file
            request.setPrimary(setAsMain && i == 0);
            ImageDTO image = imageService.uploadImage(propertyId, file, request);
            results.add(image);
        }
        
        log.info("✅ {} images uploadées avec succès", results.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(results);
    }

    @PutMapping("/{imageId}/reorder")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> reorderImages(
            @PathVariable Long imageId,
            @RequestParam Long propertyId,
            @RequestParam int newOrder) {
        log.info("🔄 Réorganisation des images pour la propriété ID: {}", propertyId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{imageId}/set-primary")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ImageDTO> setPrimaryImage(
            @PathVariable Long imageId,
            @RequestParam Long propertyId) {
        log.info("⭐ Définition image principale ID: {} pour propriété ID: {}", imageId, propertyId);
        ImageDTO updated = imageService.setPrimaryImage(imageId, propertyId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteImage(
            @PathVariable Long imageId,
            @RequestParam Long propertyId) {
        log.info("🗑️ Suppression de l'image ID: {} pour la propriété ID: {}", imageId, propertyId);
        imageService.deleteImage(imageId, propertyId);
        return ResponseEntity.ok(Map.of("success", true, "deletedId", imageId));
    }

    @DeleteMapping("/property/{propertyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteAllImages(@PathVariable Long propertyId) {
        log.info("🗑️ Suppression de toutes les images pour la propriété ID: {}", propertyId);
        imageService.deleteAllImagesByPropertyId(propertyId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}