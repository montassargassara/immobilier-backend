package com.immobilier.backend.controller;

import com.immobilier.backend.dto.MapAnythingJobDTO;
import com.immobilier.backend.service.MapAnythingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * MapAnything 3D reconstruction controller.
 *
 * CORS: no @CrossOrigin and no manual Access-Control-Allow-Origin header here —
 * the single CorsConfigurationSource bean in SecurityConfig handles CORS globally.
 */
@Slf4j
@RestController
@RequestMapping("/api/mapanything")
@RequiredArgsConstructor
public class MapAnythingController {

    private final MapAnythingService mapAnythingService;

    /** Start the GLB reconstruction pipeline for a property. */
    @PostMapping(value = "/property/{propertyId}/start",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMMERCIAL','RESPONSABLE_COMMERCIAL','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> start(
            @PathVariable Long propertyId,
            @RequestParam("file") MultipartFile file) {

        log.info("[MAP] start propertyId={} file={} size={}",
            propertyId,
            file == null ? "null" : file.getOriginalFilename(),
            file == null ? 0 : file.getSize());

        if (file == null || file.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "Video file is required"));

        try {
            MapAnythingJobDTO dto = mapAnythingService.initiate(propertyId, file);
            return ResponseEntity.accepted().body(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("[MAP] IO error propertyId={}: {}", propertyId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to save video: " + e.getMessage()));
        }
    }

    /** Poll reconstruction status for a property (frontend polling every 4 s). */
    @GetMapping("/property/{propertyId}")
    @PreAuthorize("hasAnyRole('COMMERCIAL','RESPONSABLE_COMMERCIAL','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<MapAnythingJobDTO> getStatusForProperty(@PathVariable Long propertyId) {
        return ResponseEntity.ok(mapAnythingService.getStatusForProperty(propertyId));
    }

    /**
     * Stream the raw GLB for admin preview — admin-only, JWT required.
     * Supports HTTP Range requests (206 Partial Content) for large files.
     */
    @GetMapping("/{jobId}/preview")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> preview(
            @PathVariable Long jobId,
            HttpServletRequest request) {
        try {
            Path path     = mapAnythingService.getGlbPath(jobId);
            long fileSize = Files.size(path);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("model/gltf-binary"));
            headers.setCacheControl("no-store");
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"model.glb\"");

            String rangeHeader = request.getHeader(HttpHeaders.RANGE);
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String spec  = rangeHeader.substring("bytes=".length()).split(",")[0];
                String[] pts = spec.split("-");
                long start   = pts[0].isBlank() ? 0L : Long.parseLong(pts[0].trim());
                long end     = (pts.length < 2 || pts[1].isBlank())
                    ? fileSize - 1
                    : Long.parseLong(pts[1].trim());

                if (start < 0 || end >= fileSize || start > end) {
                    headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
                    return new ResponseEntity<>(headers,
                        HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
                }

                long chunkLen = end - start + 1;
                byte[] chunk  = new byte[(int) chunkLen];
                try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
                    ch.position(start);
                    ch.read(ByteBuffer.wrap(chunk));
                }
                headers.set(HttpHeaders.CONTENT_RANGE,
                    "bytes " + start + "-" + end + "/" + fileSize);
                headers.setContentLength(chunkLen);
                return new ResponseEntity<>(chunk, headers, HttpStatus.PARTIAL_CONTENT);
            }

            headers.setContentLength(fileSize);
            return ResponseEntity.ok().headers(headers).body(new FileSystemResource(path));

        } catch (RuntimeException e) {
            log.warn("[MAP preview] Not available jobId={}: {}", jobId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("[MAP preview] IO error jobId={}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "File read error: " + e.getMessage()));
        }
    }

    /**
     * Accept the generated GLB: copy to permanent storage, create Model3D, link to property.
     * Returns the updated MapAnythingJobDTO with the public glbUrl — never an empty body.
     */
    @PostMapping("/{jobId}/accept")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> accept(@PathVariable Long jobId) {
        try {
            MapAnythingJobDTO dto = mapAnythingService.acceptJob(jobId);
            log.info("[MAP accept] jobId={} → model3dId={}", jobId, dto.getModel3dId());
            return ResponseEntity.ok(dto);
        } catch (IOException e) {
            log.error("[MAP accept] IO error jobId={}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "File copy error: " + e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("[MAP accept] jobId={}: {}", jobId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reject the generated GLB: delete temp files + work dir, set REJECTED.
     * Returns the updated MapAnythingJobDTO — never an empty body.
     */
    @PostMapping("/{jobId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> reject(@PathVariable Long jobId) {
        try {
            MapAnythingJobDTO dto = mapAnythingService.rejectJob(jobId);
            log.info("[MAP reject] jobId={} deleted", jobId);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            log.warn("[MAP reject] jobId={}: {}", jobId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
