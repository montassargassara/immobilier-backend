package com.immobilier.backend.controller;

import com.immobilier.backend.dto.VirtualTourDTO;
import com.immobilier.backend.service.VirtualTourService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/virtual-tour")
@RequiredArgsConstructor
public class VirtualTourController {

    private final VirtualTourService virtualTourService;

    // ── Initiate generation ───────────────────────────────────────────────────

    @PostMapping(value = "/generate/{propertyId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> generateTour(
            @PathVariable Long propertyId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "is360", defaultValue = "false") boolean is360) {

        log.info("Virtual tour generation requested for property {} (360={})", propertyId, is360);

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Video file is required"));
        }

        String ct = file.getContentType();
        String fn = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        boolean validVideo = (ct != null && ct.startsWith("video/"))
            || fn.endsWith(".mp4") || fn.endsWith(".mov") || fn.endsWith(".avi")
            || fn.endsWith(".mkv") || fn.endsWith(".webm");

        if (!validVideo) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported format. Accepted: MP4, MOV, AVI, MKV, WebM"));
        }

        try {
            VirtualTourDTO tour = virtualTourService.initiateGeneration(propertyId, file, is360);
            return ResponseEntity.accepted().body(tour);
        } catch (RuntimeException e) {
            log.error("Generation init failed for property {}: {}", propertyId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("IO error during generation init for property {}: {}", propertyId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save video: " + e.getMessage()));
        }
    }

    // ── Standalone generation (no propertyId required) ───────────────────────

    @PostMapping(value = "/generate-standalone", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("authenticated()")
    public ResponseEntity<?> generateStandaloneTour(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "is360", defaultValue = "false") boolean is360) {

        log.info("Standalone virtual tour generation requested (360={})", is360);

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Video file is required"));
        }

        String ct = file.getContentType();
        String fn = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        boolean validVideo = (ct != null && ct.startsWith("video/"))
            || fn.endsWith(".mp4") || fn.endsWith(".mov") || fn.endsWith(".avi")
            || fn.endsWith(".mkv") || fn.endsWith(".webm");

        if (!validVideo) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported format. Accepted: MP4, MOV, AVI, MKV, WebM"));
        }

        try {
            VirtualTourDTO tour = virtualTourService.initiateStandaloneGeneration(file, is360);
            return ResponseEntity.accepted().body(tour);
        } catch (RuntimeException e) {
            log.error("Standalone generation init failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("IO error during standalone generation init: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save video: " + e.getMessage()));
        }
    }

    // ── By-ID endpoints (used by standalone tours and future linking) ─────────

    @GetMapping("/by-id/{tourId}")
    @PreAuthorize("authenticated()")
    public ResponseEntity<VirtualTourDTO> getTourById(@PathVariable Long tourId) {
        try {
            VirtualTourDTO tour = virtualTourService.getTourById(tourId);
            return ResponseEntity.ok(tour);
        } catch (Throwable e) {
            log.error("getTourById error for tourId={}: {}", tourId, e.getMessage(), e);
            return ResponseEntity.ok(VirtualTourDTO.notCreated());
        }
    }

    @GetMapping("/status-by-id/{tourId}")
    @PreAuthorize("authenticated()")
    public ResponseEntity<VirtualTourDTO> getStatusById(@PathVariable Long tourId) {
        try {
            VirtualTourDTO tour = virtualTourService.getTourStatusById(tourId);
            return ResponseEntity.ok(tour != null ? tour : VirtualTourDTO.notCreated());
        } catch (Throwable e) {
            log.error("getStatusById error for tourId={}: {}", tourId, e.getMessage(), e);
            return ResponseEntity.ok(VirtualTourDTO.notCreated());
        }
    }

    // ── Admin: get full tour (with scenes) ────────────────────────────────────

    @GetMapping("/{propertyId}")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<VirtualTourDTO> getTour(@PathVariable Long propertyId) {
        log.info("getTour called for propertyId={}", propertyId);
        try {
            VirtualTourDTO tour = virtualTourService.getTourByPropertyId(propertyId);
            return ResponseEntity.ok(tour);
        } catch (Exception e) {
            log.error("Unhandled exception in getTour for property {}: {} [{}] — returning NOT_CREATED",
                propertyId, e.getMessage(), e.getClass().getSimpleName(), e);
            return ResponseEntity.ok(VirtualTourDTO.notCreated());
        }
    }

    // ── Admin: processing status (lightweight poll) ────────────────────────────

    @GetMapping("/status/{propertyId}")
    @PreAuthorize("authenticated()")
    public ResponseEntity<VirtualTourDTO> getStatus(@PathVariable Long propertyId) {
        // This log appears BEFORE any try/catch — if you see it in the backend console,
        // the controller is being entered and any 400 comes from the transaction proxy commit.
        // If you DON'T see it, the request is intercepted before the controller (Security/filter).
        log.warn("[CONTROLLER] getStatus ENTERED for propertyId={}", propertyId);
        try {
            VirtualTourDTO tour = virtualTourService.getTourStatus(propertyId);
            log.warn("[CONTROLLER] getStatus RETURNING 200 for propertyId={} status={}",
                propertyId, tour != null ? tour.getStatus() : "null");
            return ResponseEntity.ok(tour != null ? tour : VirtualTourDTO.notCreated());
        } catch (Throwable e) {
            // Throwable (not just Exception) to catch Errors as well
            log.error("[CONTROLLER] getStatus CAUGHT {} for propertyId={}: {}",
                e.getClass().getName(), propertyId, e.getMessage(), e);
            return ResponseEntity.ok(VirtualTourDTO.notCreated());
        }
    }

    // ── Public: get tour for visitors ─────────────────────────────────────────
    // Always returns 200 so the browser never logs a 404 for properties without a tour.
    // The Angular client checks tour.status === 'COMPLETED' before rendering the viewer.

    @GetMapping("/public/{propertyId}")
    public ResponseEntity<VirtualTourDTO> getTourPublic(@PathVariable Long propertyId) {
        try {
            VirtualTourDTO tour = virtualTourService.getTourByPropertyId(propertyId);
            return ResponseEntity.ok(tour);
        } catch (Exception e) {
            log.warn("getTourPublic: error for property {}: {}", propertyId, e.getMessage());
            return ResponseEntity.ok(VirtualTourDTO.notCreated());
        }
    }

    // ── Scene image serving (admin) ───────────────────────────────────────────

    @GetMapping("/scene-image/{tourId}/{filename}")
    @PreAuthorize("authenticated()")
    public ResponseEntity<byte[]> getSceneImage(
            @PathVariable Long tourId,
            @PathVariable String filename) {
        return serveImage(tourId, filename);
    }

    // ── Scene image serving (public) ──────────────────────────────────────────

    @GetMapping("/public/scene-image/{tourId}/{filename}")
    public ResponseEntity<byte[]> getSceneImagePublic(
            @PathVariable Long tourId,
            @PathVariable String filename) {
        return serveImage(tourId, filename);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{propertyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> deleteTour(@PathVariable Long propertyId) {
        virtualTourService.deleteTour(propertyId);
        return ResponseEntity.ok(Map.of("message", "Tour deleted successfully"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> serveImage(Long tourId, String filename) {
        try {
            byte[] data = virtualTourService.getSceneImageBytes(tourId, filename);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setContentLength(data.length);
            headers.setCacheControl("public, max-age=86400");
            return new ResponseEntity<>(data, headers, HttpStatus.OK);
        } catch (RuntimeException e) {
            log.warn("Scene image not found: tourId={} filename={}", tourId, filename);
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("Error reading scene image: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
