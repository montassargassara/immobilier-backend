package com.immobilier.backend.controller;

import com.immobilier.backend.config.GaussianSplatProperties;
import com.immobilier.backend.dto.GaussianSplatDTO;
import com.immobilier.backend.dto.Model3DDTO;
import com.immobilier.backend.repository.GaussianSplatRepository;
import com.immobilier.backend.service.GaussianSplatService;
import com.immobilier.backend.service.GaussianSplatService.PreviewEntry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/gaussian-splat")
@RequiredArgsConstructor
public class GaussianSplatController {

    private final GaussianSplatService gaussianSplatService;
    private final GaussianSplatRepository gaussianSplatRepository;
    private final GaussianSplatProperties gsProps;

    @Value("${app.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── Diagnostic (no auth — permitAll in SecurityConfig) ───────────────────
    // GET /api/gaussian-splat/diagnose
    // Tests every component of the pipeline and returns a structured report.

    @GetMapping("/diagnose")
    public ResponseEntity<Map<String, Object>> diagnose() {
        Map<String, Object> report = new LinkedHashMap<>();

        // 1. DB — does the gaussian_splats table exist and accept queries?
        try {
            long count = gaussianSplatRepository.count();
            report.put("db_gaussian_splats_table", "OK (rows: " + count + ")");
        } catch (Exception e) {
            report.put("db_gaussian_splats_table", "FAIL: " + e.getMessage());
        }

        // 2. FFmpeg
        try {
            Process p = new ProcessBuilder(ffmpegPath, "-version")
                .redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes());
            report.put("ffmpeg", done && p.exitValue() == 0
                ? "OK — " + out.lines().findFirst().orElse("?")
                : "FAIL (exit " + (done ? p.exitValue() : "timeout") + "): "
                    + out.substring(0, Math.min(200, out.length())));
        } catch (Exception e) {
            report.put("ffmpeg", "FAIL: " + e.getMessage());
        }

        // 3. Gaussian-splatting directory
        Path gsDir = Path.of(gsProps.getGsPath());
        report.put("gs_directory",
            Files.isDirectory(gsDir) ? "OK: " + gsDir : "FAIL: not found at " + gsDir);

        // 4. convert.py
        Path convertPy = gsDir.resolve("convert.py");
        report.put("convert_py", Files.exists(convertPy) ? "OK" : "FAIL: not found at " + convertPy);

        // 5. train.py
        Path trainPy = gsDir.resolve("train.py");
        report.put("train_py", Files.exists(trainPy) ? "OK" : "FAIL: not found at " + trainPy);

        // 6. COLMAP
        try {
            Process p = new ProcessBuilder(gsProps.getColmapExecutable(), "--help")
                .redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            report.put("colmap", done ? "OK (exit " + p.exitValue() + ")" : "FAIL: timeout");
        } catch (Exception e) {
            report.put("colmap", "FAIL: " + e.getMessage());
        }

        // 7. Conda
        String[] condaCandidates = {
            System.getProperty("user.home") + "/anaconda3/Scripts/conda.exe",
            System.getProperty("user.home") + "/miniconda3/Scripts/conda.exe",
            "conda"
        };
        String condaFound = null;
        for (String c : condaCandidates) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) { condaFound = c; break; }
            } catch (Exception ignored) {}
        }
        report.put("conda", condaFound != null
            ? "OK: " + condaFound
            : "NOT FOUND — pipeline will use direct python");

        // 8. Python fallback
        try {
            Process p = new ProcessBuilder(gsProps.getPythonExecutable(), "--version")
                .redirectErrorStream(true).start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes()).trim();
            report.put("python_fallback", done && p.exitValue() == 0 ? "OK: " + out : "FAIL");
        } catch (Exception e) {
            report.put("python_fallback", "FAIL: " + e.getMessage());
        }

        // 9. Work directory writable?
        Path workDir = Path.of(gsProps.getWorkDir()).toAbsolutePath();
        try {
            Files.createDirectories(workDir);
            report.put("work_dir", "OK: " + workDir);
        } catch (Exception e) {
            report.put("work_dir", "FAIL: cannot create " + workDir + " — " + e.getMessage());
        }

        report.put("config", Map.of(
            "gsPath",      gsProps.getGsPath(),
            "condaEnv",    gsProps.getCondaEnv(),
            "ffmpegPath",  ffmpegPath,
            "workDir",     gsProps.getWorkDir(),
            "baseUrl",     baseUrl
        ));

        log.info("[GS Diagnose] {}", report);
        return ResponseEntity.ok(report);
    }

    // ── Generate tied to a property (requires role) ──────────────────────────

    @PostMapping(value = "/generate/{propertyId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> generate(
            @PathVariable Long propertyId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "iterations", defaultValue = "7000") int iterations) {

        log.info("[GS] generate propertyId={} file={} size={} iterations={}",
            propertyId,
            file == null ? "null" : file.getOriginalFilename(),
            file == null ? 0 : file.getSize(),
            iterations);

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Video file is required"));
        }
        try {
            GaussianSplatDTO dto = gaussianSplatService.initiate(propertyId, file, iterations);
            log.info("[GS] generate OK propertyId={} splatId={}", propertyId, dto.getId());
            return ResponseEntity.accepted().body(dto);
        } catch (IllegalArgumentException e) {
            log.warn("[GS] generate validation error propertyId={}: {}", propertyId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("[GS] generate IO error propertyId={}: {}", propertyId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to save video: " + e.getMessage()));
        }
        // Infrastructure RuntimeExceptions (DB errors, etc.) bubble up → GlobalExceptionHandler → 500
    }

    // ── Standalone generation (no property, no auth — permitAll in SecurityConfig) ──

    @PostMapping(value = "/generate-standalone", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> generateStandalone(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "iterations", defaultValue = "7000") int iterations) {

        log.info("[GS] generateStandalone file={} size={} iterations={}",
            file == null ? "null" : file.getOriginalFilename(),
            file == null ? 0 : file.getSize(),
            iterations);

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Video file is required"));
        }
        try {
            GaussianSplatDTO dto = gaussianSplatService.initiateStandalone(file, iterations);
            log.info("[GS] generateStandalone OK splatId={}", dto.getId());
            return ResponseEntity.accepted().body(dto);
        } catch (IllegalArgumentException e) {
            log.warn("[GS] generateStandalone validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("[GS] generateStandalone IO error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to save video: " + e.getMessage()));
        }
        // Infrastructure RuntimeExceptions bubble up → GlobalExceptionHandler → 500
    }

    // ── Status polling (auth enforced by SecurityConfig wildcard) ────────────
    // NOTE: @PreAuthorize("authenticated()") is INVALID in Spring Security 6.
    // The correct SpEL is isAuthenticated(). SecurityConfig already enforces
    // authentication for /api/gaussian-splat/** via the wildcard rule — no
    // @PreAuthorize needed here.

    @GetMapping("/status/{splatId}")
    public ResponseEntity<GaussianSplatDTO> getStatus(@PathVariable Long splatId) {
        return ResponseEntity.ok(gaussianSplatService.getStatus(splatId));
    }

    @GetMapping("/status-for-property/{propertyId}")
    public ResponseEntity<GaussianSplatDTO> getStatusForProperty(@PathVariable Long propertyId) {
        return ResponseEntity.ok(gaussianSplatService.getStatusForProperty(propertyId));
    }

    // ── Serve PLY (auth from SecurityConfig wildcard) ────────────────────────

    @GetMapping("/file/{splatId}/point_cloud.ply")
    public ResponseEntity<byte[]> getPlyFile(@PathVariable Long splatId) {
        try {
            return plyResponse(gaussianSplatService.getPlyBytes(splatId));
        } catch (RuntimeException e) {
            log.warn("[GS] PLY not available splatId={}: {}", splatId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("[GS] PLY read error splatId={}: {}", splatId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Serve PLY public (permitAll in SecurityConfig) ────────────────────────

    @GetMapping("/public/file/{splatId}/point_cloud.ply")
    public ResponseEntity<byte[]> getPlyFilePublic(@PathVariable Long splatId) {
        try {
            return plyResponse(gaussianSplatService.getPlyBytes(splatId));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Streaming PLY with Content-Length header (enables download progress) ──
    // GET /api/gaussian-splat/public/{id}/stream — no auth (permitAll via /public/**)

    @GetMapping("/public/{id}/stream")
    public ResponseEntity<FileSystemResource> streamPly(@PathVariable Long id) {
        try {
            Path ply = gaussianSplatService.getPlyPath(id);
            FileSystemResource resource = new FileSystemResource(ply);
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(ply))
                .header("Content-Disposition", "inline; filename=\"point_cloud.ply\"")
                .header("Cache-Control", "public, max-age=86400")
                .body(resource);
        } catch (RuntimeException e) {
            log.warn("[GS] stream not available id={}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("[GS] stream IO error id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Public status (no auth — viewer page polls this) ─────────────────────
    // Returns only safe fields; uses the public PLY URL (no auth required).

    @GetMapping("/public/{id}/status")
    public ResponseEntity<Map<String, Object>> getPublicStatus(@PathVariable Long id) {
        try {
            GaussianSplatDTO dto = gaussianSplatService.getStatus(id);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", dto.getId());
            body.put("status", dto.getStatus());
            body.put("processingProgress", dto.getProcessingProgress() != null ? dto.getProcessingProgress() : 0);
            body.put("currentStep", dto.getCurrentStep() != null ? dto.getCurrentStep() : "");
            body.put("errorMessage", dto.getErrorMessage() != null ? dto.getErrorMessage() : "");
            body.put("iterations", dto.getIterations());
            if ("COMPLETED".equals(dto.getStatus()) && id != null) {
                // URL must end with .ply so the gaussian-splats-3d library can detect the format
                body.put("plyUrl", baseUrl + "/api/gaussian-splat/public/file/" + id + "/point_cloud.ply");
            }
            return ResponseEntity.ok(body);
        } catch (RuntimeException e) {
            log.warn("[GS] public status not found id={}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    // ── Admin validation endpoints ────────────────────────────────────────────

    /**
     * Serve the preview file (PLY or KSPLAT) for a splat AWAITING_VALIDATION or ACCEPTED.
     * Requires admin role — never public.  Supports HTTP Range requests for large files.
     */
    @GetMapping("/{splatId}/preview")
    @PreAuthorize("hasAnyRole('COMMERCIAL','RESPONSABLE_COMMERCIAL','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> getPreview(
            @PathVariable Long splatId,
            HttpServletRequest request) {
        try {
            PreviewEntry entry = gaussianSplatService.getPreviewEntry(splatId);
            Path path = entry.path();
            long fileSize = Files.size(path);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setCacheControl("no-store");
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview." + entry.format() + "\"");

            String rangeHeader = request.getHeader(HttpHeaders.RANGE);
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String rangeSpec = rangeHeader.substring("bytes=".length()).split(",")[0];
                String[] parts = rangeSpec.split("-");
                long start = parts[0].isBlank() ? 0 : Long.parseLong(parts[0].trim());
                long end   = (parts.length < 2 || parts[1].isBlank())
                        ? fileSize - 1
                        : Long.parseLong(parts[1].trim());

                if (start < 0 || end >= fileSize || start > end) {
                    headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
                    return new ResponseEntity<>(headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
                }

                long chunkLen = end - start + 1;
                byte[] chunk = new byte[(int) chunkLen];
                try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
                    ch.position(start);
                    ch.read(ByteBuffer.wrap(chunk));
                }
                headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize);
                headers.setContentLength(chunkLen);
                log.debug("[GS preview] Range {}-{}/{} splatId={}", start, end, fileSize, splatId);
                return new ResponseEntity<>(chunk, headers, HttpStatus.PARTIAL_CONTENT);
            }

            // Full file — stream via FileSystemResource to avoid loading into memory
            headers.setContentLength(fileSize);
            return ResponseEntity.ok().headers(headers).body(new FileSystemResource(path));

        } catch (RuntimeException e) {
            log.warn("[GS preview] Not available splatId={}: {}", splatId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("[GS preview] IO error splatId={}: {}", splatId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "File read error: " + e.getMessage()));
        }
    }

    /**
     * Accept the generated splat: copy to models dir, create Model3D, set ACCEPTED.
     * Returns the new Model3DDTO — never an empty body.
     */
    @PostMapping("/{splatId}/accept")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> acceptSplat(@PathVariable Long splatId) {
        try {
            Model3DDTO dto = gaussianSplatService.acceptSplat(splatId);
            log.info("[GS accept] splatId={} → model3d.id={}", splatId, dto.getId());
            return ResponseEntity.ok(dto);
        } catch (IOException e) {
            log.error("[GS accept] IO error splatId={}: {}", splatId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "File copy error: " + e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("[GS accept] splatId={}: {}", splatId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reject the generated splat: archive workDir, set REJECTED.
     */
    @PostMapping("/{splatId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> rejectSplat(@PathVariable Long splatId) {
        try {
            gaussianSplatService.rejectSplat(splatId);
            log.info("[GS reject] splatId={} archived", splatId);
            return ResponseEntity.ok(Map.of("message", "Modèle rejeté et archivé avec succès."));
        } catch (IOException e) {
            log.error("[GS reject] IO error splatId={}: {}", splatId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Archive error: " + e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("[GS reject] splatId={}: {}", splatId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<byte[]> plyResponse(byte[] data) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(data.length);
        headers.set("Content-Disposition", "inline; filename=\"point_cloud.ply\"");
        headers.setCacheControl("public, max-age=86400");
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }
}
