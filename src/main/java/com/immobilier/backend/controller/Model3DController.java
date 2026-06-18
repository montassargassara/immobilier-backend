package com.immobilier.backend.controller;

import com.immobilier.backend.dto.Model3DDTO;
import com.immobilier.backend.service.Model3DService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class Model3DController {

    private final Model3DService model3DService;

    // ==================== ROUTES PUBLIQUES ====================

    /** Serve a 3D model file by model ID. Supports HTTP Range requests for large files. */
    @GetMapping("/public/{id}")
    public ResponseEntity<?> getModelPublic(
            @PathVariable Long id,
            HttpServletRequest request) {
        try {
            log.info("📸 Public 3D model request for MODEL ID: {}", id);

            byte[] modelData = model3DService.getModel3DData(id);
            Model3DDTO modelInfo = model3DService.getModel3DInfoById(id);

            if (modelData == null || modelData.length == 0) {
                log.warn("⚠️ No data for model ID: {}", id);
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType = resolveMediaType(modelInfo);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setCacheControl("public, max-age=86400");
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.setContentDispositionFormData("inline", modelInfo.getFileName() != null ? modelInfo.getFileName() : "model");

            String rangeHeader = request.getHeader(HttpHeaders.RANGE);
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return buildRangeResponse(modelData, rangeHeader, headers, mediaType);
            }

            headers.setContentLength(modelData.length);
            log.info("✅ Serving public model ID: {}, size: {} bytes, type: {}", id, modelData.length, mediaType);
            return new ResponseEntity<>(modelData, headers, HttpStatus.OK);

        } catch (RuntimeException e) {
            log.error("❌ Model not found ID {}: {}", id, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Model not found");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            log.error("❌ Error serving model ID {}: {}", id, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /** Serve a 3D model by property ID. Supports HTTP Range requests for large files. */
    @GetMapping("/public/property/{propertyId}")
    public ResponseEntity<?> getModelByPropertyPublic(
            @PathVariable Long propertyId,
            HttpServletRequest request) {
        try {
            log.info("📸 Public 3D model request for PROPERTY ID: {}", propertyId);

            Model3DDTO modelInfo = model3DService.getModel3DInfoByPropertyId(propertyId);
            if (modelInfo == null) {
                log.warn("⚠️ No model found for property ID: {}", propertyId);
                return ResponseEntity.notFound().build();
            }

            byte[] modelData = model3DService.getModel3DData(modelInfo.getId());
            if (modelData == null || modelData.length == 0) {
                log.warn("⚠️ No data for model ID: {}", modelInfo.getId());
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType = resolveMediaType(modelInfo);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setCacheControl("public, max-age=86400");
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.setContentDispositionFormData("inline", modelInfo.getFileName() != null ? modelInfo.getFileName() : "model");

            String rangeHeader = request.getHeader(HttpHeaders.RANGE);
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return buildRangeResponse(modelData, rangeHeader, headers, mediaType);
            }

            headers.setContentLength(modelData.length);
            log.info("✅ Serving model for property ID: {}, model ID: {}, size: {} bytes",
                    propertyId, modelInfo.getId(), modelData.length);
            return new ResponseEntity<>(modelData, headers, HttpStatus.OK);

        } catch (RuntimeException e) {
            log.error("❌ Model not found for property ID {}: {}", propertyId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Model not found for this property");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        } catch (Exception e) {
            log.error("❌ Error serving model for property ID {}: {}", propertyId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/public/{id}/info")
    public ResponseEntity<?> getModelInfoPublic(@PathVariable Long id) {
        try {
            Model3DDTO model = model3DService.getModel3DInfoById(id);
            return model != null ? ResponseEntity.ok(model) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error getting model info: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Model not found");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @GetMapping("/public/property/{propertyId}/info")
    public ResponseEntity<?> getModelInfoByPropertyPublic(@PathVariable Long propertyId) {
        try {
            Model3DDTO model = model3DService.getModel3DInfoByPropertyId(propertyId);
            return model != null ? ResponseEntity.ok(model) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error getting model info for property {}: {}", propertyId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Model not found for this property");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    // ==================== ROUTES PROTÉGÉES ====================

    @GetMapping("/property/{propertyId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getModelByProperty(@PathVariable Long propertyId) {
        try {
            log.info("🔒 Récupération du modèle 3D pour la propriété ID: {}", propertyId);
            Model3DDTO model = model3DService.getModel3DInfoByPropertyId(propertyId);
            return model != null ? ResponseEntity.ok(model) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error getting model: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping(value = "/property/{propertyId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> uploadModel(
            @PathVariable Long propertyId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {

        log.info("🎮 Upload de modèle 3D pour la propriété ID: {}", propertyId);

        try {
            if (file == null || file.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            if (file.getSize() > 1024L * 1024 * 1024) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "File too large. Maximum size: 1GB");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            String contentType = file.getContentType();
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            boolean validExtension = fileName.endsWith(".glb") || fileName.endsWith(".gltf")
                    || fileName.endsWith(".obj") || fileName.endsWith(".fbx") || fileName.endsWith(".ply")
                    || fileName.endsWith(".ksplat") || fileName.endsWith(".splat");
            boolean validMime = contentType != null && (
                    contentType.contains("gltf") || contentType.contains("glb")
                    || contentType.contains("obj") || contentType.contains("fbx")
                    || contentType.contains("ply") || contentType.contains("model")
                    || contentType.equals("application/octet-stream"));
            if (!validExtension && !validMime) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Format non supporté. Formats acceptés : GLB, GLTF, OBJ, FBX, PLY, KSPLAT, SPLAT");
                error.put("supported", "GLB, GLTF, OBJ, FBX, PLY, KSPLAT, SPLAT");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }

            Model3DDTO model = model3DService.uploadModel3D(propertyId, file, description);
            return ResponseEntity.status(HttpStatus.CREATED).body(model);

        } catch (RuntimeException e) {
            log.error("❌ Business error uploading model: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (IOException e) {
            log.error("❌ IO error uploading model: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to process file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception e) {
            log.error("❌ Unexpected error uploading model: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @DeleteMapping("/{modelId}")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> deleteModel(
            @PathVariable Long modelId,
            @RequestParam Long propertyId) {
        try {
            log.info("🗑️ Suppression du modèle 3D ID: {} pour la propriété ID: {}", modelId, propertyId);
            model3DService.deleteModel3D(modelId, propertyId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Modèle 3D supprimé avec succès");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error deleting model: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @DeleteMapping("/property/{propertyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> deleteAllModels(@PathVariable Long propertyId) {
        try {
            log.info("🗑️ Suppression de tous les modèles 3D pour la propriété ID: {}", propertyId);
            model3DService.deleteModel3DByPropertyId(propertyId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Tous les modèles 3D ont été supprimés avec succès");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error deleting models: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ==================== HELPERS ====================

    private MediaType resolveMediaType(Model3DDTO modelInfo) {
        String fmt = modelInfo.getFormat();
        if (fmt != null) {
            switch (fmt.toLowerCase()) {
                case "glb":   return MediaType.parseMediaType("model/gltf-binary");
                case "gltf":  return MediaType.parseMediaType("model/gltf+json");
                case "ksplat":
                case "splat": return MediaType.APPLICATION_OCTET_STREAM;
                case "ply":   return MediaType.APPLICATION_OCTET_STREAM;
                default: break;
            }
        }
        if (modelInfo.getFileType() != null && !modelInfo.getFileType().isBlank()) {
            try { return MediaType.parseMediaType(modelInfo.getFileType()); } catch (Exception ignored) {}
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * Parse a single Range header and return a 206 Partial Content response.
     * Handles the common {@code bytes=start-end} form; multi-range is not supported.
     */
    private ResponseEntity<byte[]> buildRangeResponse(
            byte[] data, String rangeHeader, HttpHeaders headers, MediaType mediaType) {
        try {
            String rangeSpec = rangeHeader.substring("bytes=".length());
            // Only support single range (most clients send one range at a time)
            String[] parts = rangeSpec.split(",")[0].split("-");
            long start = parts[0].isBlank() ? 0 : Long.parseLong(parts[0].trim());
            long end   = (parts.length < 2 || parts[1].isBlank())
                    ? data.length - 1
                    : Long.parseLong(parts[1].trim());

            if (start < 0 || end >= data.length || start > end) {
                // Unsatisfiable range
                HttpHeaders errHeaders = new HttpHeaders();
                errHeaders.set(HttpHeaders.CONTENT_RANGE, "bytes */" + data.length);
                return new ResponseEntity<>(errHeaders, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }

            byte[] chunk = Arrays.copyOfRange(data, (int) start, (int) end + 1);
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + data.length);
            headers.setContentLength(chunk.length);
            headers.setContentType(mediaType);

            log.debug("Serving range bytes={}-{}/{} ({} bytes)", start, end, data.length, chunk.length);
            return new ResponseEntity<>(chunk, headers, HttpStatus.PARTIAL_CONTENT);

        } catch (NumberFormatException e) {
            // Malformed Range header — serve the whole file
            headers.setContentLength(data.length);
            return new ResponseEntity<>(data, headers, HttpStatus.OK);
        }
    }
}
