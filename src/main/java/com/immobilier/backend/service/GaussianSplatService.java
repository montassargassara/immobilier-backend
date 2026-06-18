package com.immobilier.backend.service;

import com.immobilier.backend.config.GaussianSplatProperties;
import com.immobilier.backend.dto.GaussianSplatDTO;
import com.immobilier.backend.dto.Model3DDTO;
import com.immobilier.backend.entity.GaussianSplat;
import com.immobilier.backend.entity.Model3D;
import com.immobilier.backend.repository.GaussianSplatRepository;
import com.immobilier.backend.repository.Model3DRepository;
import com.immobilier.backend.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GaussianSplatService {

    private final GaussianSplatRepository splatRepository;
    private final Model3DRepository model3DRepository;
    private final PropertyRepository propertyRepository;

    @Autowired @Lazy
    private GaussianSplatService self;

    private final GaussianSplatProperties gsProps;

    @Value("${app.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${app.ffprobe.path:ffprobe}")
    private String ffprobePath;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${file.upload.models-dir:uploads/models}")
    private String modelsDir;

    /** Carries the preview file location and its format string ('ply', 'ksplat', …). */
    public record PreviewEntry(Path path, String format) {}

    /** Python script written to a temp file before each blur-filter run. */
    private static final String BLUR_FILTER_SCRIPT = """
            import sys, os, glob
            import numpy as np

            threshold = float(sys.argv[1])
            min_keep  = int(sys.argv[2])
            input_dir = sys.argv[3]

            try:
                import cv2
                def score(p):
                    img = cv2.imread(p, cv2.IMREAD_GRAYSCALE)
                    return float(cv2.Laplacian(img, cv2.CV_64F).var()) if img is not None else 0.0
            except ImportError:
                from PIL import Image
                from scipy.ndimage import laplace
                def score(p):
                    try:
                        arr = np.array(Image.open(p).convert('L'), dtype=float)
                        return float(laplace(arr).var())
                    except Exception:
                        return 0.0

            frames = sorted(glob.glob(os.path.join(input_dir, '*.jpg')))
            if not frames:
                print('Blur filter: no frames found', flush=True)
                sys.exit(0)

            scored = [(f, score(f)) for f in frames]
            scored.sort(key=lambda x: x[1], reverse=True)   # sharpest first

            # Always keep the top min_keep frames regardless of threshold
            keep = set(f for f, _ in scored[:min_keep])
            for f, s in scored[min_keep:]:
                if s >= threshold:
                    keep.add(f)

            removed = 0
            for f, _ in scored:
                if f not in keep:
                    try:
                        os.remove(f)
                        removed += 1
                    except OSError:
                        pass

            kept = len(frames) - removed
            print(f'Blur filter: {len(frames)} frames -> {kept} kept, {removed} removed (threshold={threshold})', flush=True)
            """;

    // Matches tqdm lines like "  14%|██        | 1000/7000 [00:23<02:37,  6.34it/s]"
    private static final Pattern TQDM_ITER = Pattern.compile("(\\d+)/(\\d+)");

    // ── Initiate (property-tied) ──────────────────────────────────────────────

    @Transactional
    public GaussianSplatDTO initiate(Long propertyId, MultipartFile videoFile, int iterations)
            throws IOException {
        if (propertyId != null) {
            splatRepository.findAllByPropertyIdDesc(propertyId).forEach(old -> {
                deleteSplatFiles(old);
                splatRepository.delete(old);
            });
        }
        GaussianSplat splat = createAndSave(propertyId, videoFile, iterations);
        self.runPipelineAsync(splat.getId());
        return GaussianSplatDTO.from(splat, baseUrl);
    }

    // ── Initiate standalone (no property) ────────────────────────────────────

    @Transactional
    public GaussianSplatDTO initiateStandalone(MultipartFile videoFile, int iterations)
            throws IOException {
        GaussianSplat splat = createAndSave(null, videoFile, iterations);
        self.runPipelineAsync(splat.getId());
        return GaussianSplatDTO.from(splat, baseUrl);
    }

    // ── Save video + create DB record ────────────────────────────────────────

    private GaussianSplat createAndSave(Long propertyId, MultipartFile videoFile,
                                         int iterations) throws IOException {
        log.info("[GS] createAndSave propertyId={} iterations={} fileSize={}B",
            propertyId, iterations, videoFile.getSize());

        GaussianSplat splat = new GaussianSplat();
        splat.setPropertyId(propertyId);
        splat.setStatus("PENDING");
        splat.setIterations(iterations);

        log.info("[GS] Inserting row into gaussian_splats table...");
        splat = splatRepository.save(splat); // IDENTITY strategy → immediate flush
        log.info("[GS] DB insert OK — splatId={}", splat.getId());

        Long id = splat.getId();
        if (id == null) throw new IllegalStateException("DB did not return a generated ID");

        Path jobDir = Path.of(gsProps.getWorkDir(), String.valueOf(id)).toAbsolutePath();
        Files.createDirectories(jobDir.resolve("input"));

        String raw = videoFile.getOriginalFilename();
        String origName = (raw != null && !raw.isBlank()) ? raw : "input.mp4";
        String stored = "source_" + System.currentTimeMillis() + "_"
            + origName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        Path videoPath = jobDir.resolve(stored);
        try (InputStream is = videoFile.getInputStream()) {
            Files.copy(is, videoPath, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("[GS] Video saved: {} ({}B)", videoPath, Files.size(videoPath));

        splat.setSourceVideoPath(videoPath.toAbsolutePath().toString());
        splat.setWorkDir(jobDir.toAbsolutePath().toString());
        return splatRepository.save(splat);
    }

    // ── Status / serve ────────────────────────────────────────────────────────

    public GaussianSplatDTO getStatus(Long splatId) {
        if (splatId == null) return GaussianSplatDTO.notCreated();
        return splatRepository.findById(splatId)
            .map(g -> GaussianSplatDTO.from(g, baseUrl))
            .orElse(GaussianSplatDTO.notCreated());
    }

    public GaussianSplatDTO getStatusForProperty(Long propertyId) {
        List<GaussianSplat> rows = splatRepository.findAllByPropertyIdDesc(propertyId);
        if (rows.isEmpty()) return GaussianSplatDTO.notCreated();
        return GaussianSplatDTO.from(rows.get(0), baseUrl);
    }

    public byte[] getPlyBytes(Long splatId) throws IOException {
        return Files.readAllBytes(getPlyPath(splatId));
    }

    public Path getPlyPath(Long splatId) throws IOException {
        if (splatId == null) throw new RuntimeException("splatId is required");
        GaussianSplat splat = splatRepository.findById(splatId)
            .orElseThrow(() -> new RuntimeException("Gaussian splat not found: " + splatId));
        String st = splat.getStatus();
        if (!"COMPLETED".equals(st) && !"AWAITING_VALIDATION".equals(st) && !"ACCEPTED".equals(st))
            throw new RuntimeException("Gaussian splat not ready: " + st);
        Path ply = resolvePlyPath(splat);
        if (!Files.exists(ply)) throw new RuntimeException("PLY file not found on disk: " + ply);
        return ply;
    }

    // ── Admin validation workflow ─────────────────────────────────────────────

    /**
     * Called at the end of a successful pipeline run.
     * Stores the PLY as the preview file and sets status to AWAITING_VALIDATION.
     * No auto-publish to Model3D — admin must explicitly accept first.
     */
    private void finalizeAwaitingValidation(GaussianSplat splat, Path plyFile) {
        splat.setPlyFilePath(plyFile.toAbsolutePath().toString());
        splat.setPreviewFilePath(plyFile.toAbsolutePath().toString());
        splat.setPreviewFormat("ply");
        splat.setStatus("AWAITING_VALIDATION");
        splat.setProcessingProgress(100);
        splat.setCurrentStep("Visite 3D générée — en attente de validation");
        splatRepository.save(splat);
        log.info("[GS] AWAITING_VALIDATION splatId={} preview={}", splat.getId(), plyFile);
    }

    /**
     * Returns the preview file path + format for a splat that is AWAITING_VALIDATION or ACCEPTED.
     * Used by the preview endpoint to serve the file (with Range support).
     */
    public PreviewEntry getPreviewEntry(Long splatId) throws IOException {
        GaussianSplat splat = splatRepository.findById(splatId)
            .orElseThrow(() -> new RuntimeException("Gaussian splat not found: " + splatId));
        String st = splat.getStatus();
        if (!"AWAITING_VALIDATION".equals(st) && !"ACCEPTED".equals(st))
            throw new IllegalStateException("Preview not available: status=" + st);
        if (splat.getPreviewFilePath() == null)
            throw new IllegalStateException("No preview file for splatId: " + splatId);
        Path path = Path.of(splat.getPreviewFilePath());
        if (!Files.exists(path))
            throw new IOException("Preview file missing on disk: " + path);
        String fmt = splat.getPreviewFormat() != null ? splat.getPreviewFormat() : "ply";
        return new PreviewEntry(path, fmt);
    }

    /**
     * Accept: copy the preview file to uploads/models/, create/update Model3D, set status=ACCEPTED.
     * Returns the new Model3DDTO — never an empty body.
     */
    @Transactional
    public Model3DDTO acceptSplat(Long splatId) throws IOException {
        GaussianSplat splat = splatRepository.findById(splatId)
            .orElseThrow(() -> new RuntimeException("Gaussian splat not found: " + splatId));
        if (!"AWAITING_VALIDATION".equals(splat.getStatus()))
            throw new IllegalStateException("Cannot accept: status=" + splat.getStatus());
        if (splat.getPreviewFilePath() == null)
            throw new IllegalStateException("No preview file to accept for splatId: " + splatId);
        if (splat.getPropertyId() == null)
            throw new IllegalStateException("Splat has no linked property: " + splatId);

        Path src = Path.of(splat.getPreviewFilePath());
        if (!Files.exists(src))
            throw new IOException("Preview file not found: " + src);

        String ext = splat.getPreviewFormat() != null ? splat.getPreviewFormat() : "ply";
        Path destDir = Path.of(modelsDir).toAbsolutePath();
        Files.createDirectories(destDir);
        String fileName = "gs_" + splatId + "." + ext;
        Path dest = destDir.resolve(fileName);
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        log.info("[GS] Accepted splat {} → {}", splatId, dest);

        // Soft-delete existing active Model3D records for this property
        model3DRepository.findByPropertyId(splat.getPropertyId())
            .forEach(m -> { m.setIsActive(false); model3DRepository.save(m); });

        // 'splat-ply' signals "gaussian-splat PLY" to the frontend router
        // (as opposed to 'ply' which is a mesh PLY)
        String modelFormat = "ply".equals(ext) ? "splat-ply" : ext;

        Model3D model = new Model3D();
        model.setPropertyId(splat.getPropertyId());
        model.setFileName(fileName);
        model.setFileType("application/octet-stream");
        model.setFileSize(Files.size(dest));
        model.setModelPath(dest.toAbsolutePath().toString());
        model.setFormat(modelFormat);
        model.setIsActive(true);
        model.setDescription("Gaussian Splatting 3D — généré automatiquement");
        Model3D saved = model3DRepository.save(model);

        propertyRepository.findById(splat.getPropertyId()).ifPresent(p -> {
            p.setMainModel3dId(saved.getId());
            propertyRepository.save(p);
        });

        splat.setStatus("ACCEPTED");
        splatRepository.save(splat);
        log.info("[GS] ACCEPTED splatId={} → model3d.id={}", splatId, saved.getId());

        Model3DDTO dto = new Model3DDTO();
        dto.setId(saved.getId());
        dto.setPropertyId(saved.getPropertyId());
        dto.setFileName(saved.getFileName());
        dto.setFileType(saved.getFileType());
        dto.setFileSize(saved.getFileSize());
        dto.setFormat(saved.getFormat());
        dto.setDescription(saved.getDescription());
        dto.setUrl("/api/models/public/" + saved.getId());
        dto.setCreatedAt(saved.getCreatedAt());
        return dto;
    }

    /**
     * Reject: archive the work directory, set status=REJECTED.
     */
    @Transactional
    public void rejectSplat(Long splatId) throws IOException {
        GaussianSplat splat = splatRepository.findById(splatId)
            .orElseThrow(() -> new RuntimeException("Gaussian splat not found: " + splatId));
        if (!"AWAITING_VALIDATION".equals(splat.getStatus()))
            throw new IllegalStateException("Cannot reject: status=" + splat.getStatus());

        if (splat.getWorkDir() != null) {
            Path workDir = Path.of(splat.getWorkDir()).toAbsolutePath();
            if (Files.exists(workDir)) {
                Path archiveBase = workDir.getParent().resolveSibling("gaussian-archive");
                Files.createDirectories(archiveBase);
                Path archiveDest = archiveBase.resolve(String.valueOf(splatId));
                try {
                    if (Files.exists(archiveDest)) {
                        // Remove stale archive entry before moving
                        try (var s = Files.walk(archiveDest)) {
                            s.sorted(Comparator.reverseOrder())
                                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                        }
                    }
                    Files.move(workDir, archiveDest, StandardCopyOption.REPLACE_EXISTING);
                    log.info("[GS] REJECTED splatId={} — archived to {}", splatId, archiveDest);
                } catch (IOException e) {
                    log.warn("[GS] Could not archive workDir splatId={}: {}", splatId, e.getMessage());
                }
            }
        }

        splat.setStatus("REJECTED");
        splat.setCurrentStep("Rejeté par l'administrateur");
        splatRepository.save(splat);
    }

    private Path resolvePlyPath(GaussianSplat splat) {
        if (splat.getPlyFilePath() != null) return Path.of(splat.getPlyFilePath());
        return Path.of(splat.getWorkDir(), "point_cloud",
            "iteration_" + splat.getIterations(), "point_cloud.ply");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ASYNC PIPELINE
    // ─────────────────────────────────────────────────────────────────────────

    @Async
    public void runPipelineAsync(Long splatId) {
        if (splatId == null) {
            log.error("[GS][ERROR] runPipelineAsync called with null splatId — aborting");
            return;
        }
        log.info("[GS] ========== PIPELINE START splatId={} ==========", splatId);

        // The parent @Transactional may not have committed yet — retry up to 1 s
        GaussianSplat splat = null;
        for (int i = 0; i < 10; i++) {
            splat = splatRepository.findById(splatId).orElse(null);
            if (splat != null) break;
            try { Thread.sleep(100); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (splat == null) {
            log.error("[GS][ERROR] splatId={} not found after 1 s — aborting", splatId);
            return;
        }

        try {
            Path jobDir   = Path.of(splat.getWorkDir());
            Path videoPath = Path.of(splat.getSourceVideoPath());
            Path inputDir  = jobDir.resolve("input");

            // ── Pre-flight checks ────────────────────────────────────────────
            if (!Files.exists(videoPath)) {
                throw new RuntimeException("[GS][ERROR] Source video not found: " + videoPath
                    + " — check that the file was saved correctly during upload");
            }
            log.info("[GS] Source video OK: {} ({}B)", videoPath, Files.size(videoPath));
            log.info("[GS] Job directory: {}", jobDir.toAbsolutePath());

            // ── STEP 1: FFmpeg frame extraction ──────────────────────────────
            log.info("[GS] ── STEP 1/4: FFmpeg frame extraction ──");
            updateStatus(splat, "PROCESSING", 5, "Extraction des images (FFmpeg)...");
            extractFrames(videoPath, inputDir);

            long frameCount = countFiles(inputDir, ".jpg");
            log.info("[GS] Frames extracted: {}", frameCount);
            if (frameCount < 5) {
                throw new RuntimeException(
                    "[GS][ERROR] FFmpeg produced only " + frameCount + " frames "
                    + "(minimum 5). The video may be too short, corrupt, or "
                    + "FFmpeg could not decode it. FFmpeg path: " + ffmpegPath);
            }
            updateStatus(splat, "PROCESSING", 18, frameCount + " images extraites");

            // ── STEP 2: Blur frame filtering ─────────────────────────────────
            log.info("[GS] ── STEP 2/4: Blur frame filtering ──");
            updateStatus(splat, "PROCESSING", 20, "Filtrage des images floues...");
            filterBlurryFrames(inputDir);

            long filteredCount = countFiles(inputDir, ".jpg");
            log.info("[GS] Frames after blur filter: {}", filteredCount);
            if (filteredCount < 5) {
                throw new RuntimeException(
                    "[GS][ERROR] Only " + filteredCount + " frames remain after blur filtering "
                    + "(threshold=" + gsProps.getBlurThreshold() + "). "
                    + "Lower app.gaussian.blur-threshold or capture a less shaky video.");
            }
            updateStatus(splat, "PROCESSING", 25, filteredCount + " images nettes conservées");

            // ── STEP 3: COLMAP SfM via convert.py ───────────────────────────
            log.info("[GS] ── STEP 3/4: COLMAP Structure-from-Motion ──");
            updateStatus(splat, "PROCESSING", 30, "Structure-from-Motion (COLMAP)...");
            runColmap(jobDir);
            logColmapStats(jobDir);
            updateStatus(splat, "PROCESSING", 55, "Calibration COLMAP terminée");

            // ── STEP 4: Gaussian Splatting training ──────────────────────────
            log.info("[GS] ── STEP 4/4: Gaussian Splatting training ({} iter) ──",
                splat.getIterations());
            updateStatus(splat, "PROCESSING", 60, "Entraînement Gaussian Splatting...");
            runTraining(jobDir, splat.getIterations(), splat);

            // ── Find PLY output ──────────────────────────────────────────────
            Path plyFile = locatePlyFile(jobDir, splat.getIterations());
            log.info("[GS] PLY file: {} ({}B)", plyFile, Files.size(plyFile));

            finalizeAwaitingValidation(splat, plyFile);
            log.info("[GS] ========== PIPELINE AWAITING_VALIDATION splatId={} ==========", splatId);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            log.error("[GS][ERROR] Pipeline FAILED splatId={}: {}", splatId, msg, e);
            splat.setStatus("FAILED");
            splat.setErrorMessage(msg);                          // LONGTEXT column — no truncation needed
            splat.setCurrentStep(truncate("Erreur: " + msg, 500)); // TEXT column
            splat.setProcessingProgress(0);
            log.info("[GS] Saving FAILED status for splatId={}", splatId);
            splatRepository.save(splat);
            log.info("[GS] FAILED status saved OK");
        }
    }

    // ── STEP 1: frame extraction ─────────────────────────────────────────────

    private void extractFrames(Path videoPath, Path outputDir)
            throws IOException, InterruptedException {
        Files.createDirectories(outputDir);
        // Compute fps to hit the configured target frame count uniformly
        double duration = getVideoDuration(videoPath);
        int target = gsProps.getTargetFrames();
        // Clamp: 0.5 fps minimum (prevents too-sparse coverage), 5 fps maximum
        double fps = Math.min(5.0, Math.max(0.5, target / Math.max(duration, 1.0)));
        log.info("[GS] Frame extraction: duration={}s target={} → fps={}",
            String.format(Locale.US, "%.1f", duration), target, String.format(Locale.US, "%.3f", fps));
        List<String> cmd = List.of(
            ffmpegPath,
            "-y",
            "-i",  videoPath.toAbsolutePath().toString(),
            "-vf", "fps=" + String.format(Locale.US, "%.4f", fps),
            "-q:v", "1",
            outputDir.resolve("frame_%04d.jpg").toAbsolutePath().toString()
        );
        runProcess(cmd, "FFmpeg frame extraction", 10, null, 0, 0, 0, null);
    }

    /** Returns video duration in seconds via ffprobe; falls back to 60 s on any error. */
    private double getVideoDuration(Path videoPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                ffprobePath, "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(10, TimeUnit.SECONDS);
            if (!out.isBlank()) return Double.parseDouble(out);
        } catch (Exception e) {
            log.warn("[GS] ffprobe duration failed: {} — assuming 60 s", e.getMessage());
        }
        return 60.0;
    }

    // ── STEP 2: blur frame filtering ─────────────────────────────────────────

    /** Removes blurry frames (low Laplacian variance) from inputDir before COLMAP. */
    private void filterBlurryFrames(Path inputDir) throws IOException, InterruptedException {
        double threshold = gsProps.getBlurThreshold();
        if (threshold <= 0) {
            log.info("[GS] Blur filtering disabled (threshold={})", threshold);
            return;
        }
        Path scriptPath = inputDir.getParent().resolve("blur_filter.py");
        Files.writeString(scriptPath, BLUR_FILTER_SCRIPT);
        try {
            List<String> cmd = buildPythonCmd(List.of(
                scriptPath.toAbsolutePath().toString(),
                String.format(Locale.US, "%.4f", threshold),
                "120",   // always keep at least 120 frames even if below threshold
                inputDir.toAbsolutePath().toString()
            ));
            runProcess(cmd, "Blur filter", 5, null, 0, 0, 0, null);
        } finally {
            try { Files.deleteIfExists(scriptPath); } catch (IOException ignored) {}
        }
    }

    // ── STEP 3 (support): COLMAP stats logging ───────────────────────────────

    /** Reads the COLMAP sparse model and logs registered-image and 3D-point counts. */
    private void logColmapStats(Path jobDir) {
        try {
            Path sparse = jobDir.resolve("sparse").resolve("0");
            if (!Files.exists(sparse)) {
                log.warn("[GS COLMAP] Sparse model not found at {} — reconstruction may have failed", sparse);
                return;
            }

            // images.bin: first 8 bytes = uint64 registered-image count
            long imageCount = -1;
            Path imagesBin = sparse.resolve("images.bin");
            Path imagesTxt = sparse.resolve("images.txt");
            if (Files.exists(imagesBin)) {
                try (var is = Files.newInputStream(imagesBin)) {
                    byte[] h = is.readNBytes(8);
                    if (h.length == 8)
                        imageCount = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).getLong();
                }
            } else if (Files.exists(imagesTxt)) {
                imageCount = Files.lines(imagesTxt)
                    .filter(l -> !l.startsWith("#") && !l.isBlank())
                    .count() / 2;
            }

            // points3D.bin: first 8 bytes = uint64 point count
            long pointCount = -1;
            Path points3dBin = sparse.resolve("points3D.bin");
            if (Files.exists(points3dBin)) {
                try (var is = Files.newInputStream(points3dBin)) {
                    byte[] h = is.readNBytes(8);
                    if (h.length == 8)
                        pointCount = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).getLong();
                }
            }

            log.info("[GS COLMAP stats] Registered images={} | 3D points={}", imageCount, pointCount);
            if (imageCount >= 0 && imageCount < 20)
                log.warn("[GS COLMAP] Only {} images registered — poor reconstruction expected. "
                    + "Increase target-frames or improve video coverage.", imageCount);
            if (pointCount >= 0 && pointCount < 1000)
                log.warn("[GS COLMAP] Only {} 3D points — sparse model is weak. "
                    + "More frames or better scene coverage recommended.", pointCount);
        } catch (Exception e) {
            log.warn("[GS COLMAP] Could not read sparse model stats: {}", e.getMessage());
        }
    }

    // ── STEP 3: COLMAP via convert.py ────────────────────────────────────────

    private void runColmap(Path jobDir) throws IOException, InterruptedException {
        // ── Validate convert.py ──────────────────────────────────────────────
        Path script = Path.of(gsProps.getGsPath(), "convert.py");
        if (!Files.exists(script)) {
            throw new RuntimeException(
                "[GS][ERROR] convert.py not found at: " + script
                + " — verify app.gaussian.gs-path in application.properties");
        }

        // ── Validate COLMAP executable ───────────────────────────────────────
        String colmapExe = gsProps.getColmapExecutable();
        Path colmapPath = Path.of(colmapExe);
        if (!Files.exists(colmapPath)) {
            throw new RuntimeException(
                "[GS][ERROR] COLMAP executable not found at: " + colmapPath.toAbsolutePath()
                + " — set app.gaussian.colmap-executable in application.properties "
                + "(current value: " + colmapExe + ")");
        }

        log.info("[GS] COLMAP executable : {}", colmapPath.toAbsolutePath());
        log.info("[GS] convert.py        : {}", script.toAbsolutePath());
        log.info("[GS] Job directory     : {}", jobDir.toAbsolutePath());

        // ── Build command ────────────────────────────────────────────────────
        // On Windows, .bat files must be launched via "cmd /c" — ProcessBuilder
        // cannot execute them directly (only .exe files are directly launchable).
        String colmapArg = colmapPath.toAbsolutePath().toString();
        List<String> pythonArgs = List.of(
            script.toAbsolutePath().toString(),
            "-s", jobDir.toAbsolutePath().toString(),
            "--colmap_executable", colmapArg
        );
        List<String> cmd = buildPythonCmd(pythonArgs);

        log.info("[GS] COLMAP full command: {}", String.join(" ", cmd));

        // COLMAP timeout: 30 min (dense reconstruction can be slow)
        runProcess(cmd, "COLMAP SfM", 30, null, 0, 0, 0, jobDir.toFile());
    }

    // ── STEP 3: Gaussian Splatting training ──────────────────────────────────

    private void runTraining(Path jobDir, int iterations, GaussianSplat splat)
            throws IOException, InterruptedException {
        Path script = Path.of(gsProps.getGsPath(), "train.py");
        if (!Files.exists(script)) {
            throw new RuntimeException(
                "[GS][ERROR] train.py not found at: " + script);
        }
        log.info("[GS] train.py: {}", script);

        List<String> cmd = buildPythonCmd(List.of(
            script.toAbsolutePath().toString(),
            "-s", jobDir.toAbsolutePath().toString(),
            "-m", jobDir.toAbsolutePath().toString(),
            "--iterations",     String.valueOf(iterations),
            "--save_iterations", String.valueOf(iterations),
            "--test_iterations", String.valueOf(iterations)
        ));

        // Training timeout: 4 hours (GPU training at 7000 iter ≈ 10–30 min on RTX)
        runProcess(cmd, "Gaussian training", 240, splat, 60, 92, iterations,
            Path.of(gsProps.getGsPath()).toFile());
    }

    // ── Find PLY output (several possible locations) ─────────────────────────

    private Path locatePlyFile(Path jobDir, int iterations) throws IOException {
        // Gaussian-splatting saves: <model_dir>/point_cloud/iteration_<N>/point_cloud.ply
        Path canonical = jobDir.resolve("point_cloud")
            .resolve("iteration_" + iterations)
            .resolve("point_cloud.ply");
        if (Files.exists(canonical)) return canonical;

        // Search recursively for any .ply under jobDir (handles edge-case output paths)
        try (var stream = Files.walk(jobDir)) {
            return stream
                .filter(p -> p.getFileName().toString().endsWith(".ply"))
                .max(Comparator.comparingLong(p -> {
                    try { return Files.size(p); } catch (IOException e) { return 0L; }
                }))
                .orElseThrow(() -> new RuntimeException(
                    "[GS][ERROR] No .ply file produced. Training may have failed silently. "
                    + "Expected at: " + canonical
                    + " — check backend logs for Python errors."));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CORE PROCESS RUNNER — FIXED
    //
    // THE BUG in the previous version:
    //   process.getInputStream().readAllBytes()  ← blocks with NO timeout
    //   process.waitFor(N, MINUTES)              ← never reached
    //
    // THE FIX:
    //   A daemon thread drains stdout/stderr continuously (prevents OS pipe
    //   buffer deadlock AND logs each line in real time).
    //   The calling thread uses waitFor(timeout) — which actually runs now.
    //   If the process hangs → destroyForcibly() → drainer thread unblocks.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param progressStart  DB progress % to write when training output matches
     * @param progressEnd    upper bound % for training progress
     * @param totalIterations  expected total iterations (for %-parsing); 0 = disabled
     * @param splat          entity to update mid-run (null = no mid-run updates)
     * @param workDir        ProcessBuilder working directory (null = inherit)
     */
    private void runProcess(
            List<String> cmd,
            String label,
            int timeoutMinutes,
            GaussianSplat splat,
            int progressStart,
            int progressEnd,
            int totalIterations,
            File workDir)
            throws IOException, InterruptedException {

        log.info("[GS][{}] CMD: {}", label, String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true); // merge stderr into stdout — ONE stream to drain
        pb.environment().put("PYTHONUNBUFFERED", "1"); // Python: no line buffering
        if (workDir != null) pb.directory(workDir);

        Process process = pb.start();
        log.info("[GS][{}] Process started (PID {})", label, process.pid());

        // ── Drain stdout+stderr in a background daemon thread ────────────────
        // This is mandatory to prevent OS pipe-buffer deadlock.
        // Without it, if the process writes > ~64 KB, it blocks waiting for
        // Java to read, while Java blocks waiting for the process to exit.
        StringBuilder outputBuf = new StringBuilder(4096);
        long[] lastProgressSave = {System.currentTimeMillis()};

        Thread drainer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[GS][{}] {}", label, line);
                    synchronized (outputBuf) { outputBuf.append(line).append('\n'); }

                    // Update DB progress during training based on tqdm output
                    if (splat != null && totalIterations > 0) {
                        maybePersistTrainingProgress(
                            line, splat, progressStart, progressEnd,
                            totalIterations, lastProgressSave);
                    }
                }
            } catch (IOException e) {
                log.warn("[GS][{}] Drainer interrupted: {}", label, e.getMessage());
            }
        }, "gs-drainer-" + label.replace(' ', '-'));
        drainer.setDaemon(true);
        drainer.start();

        // ── Wait with real timeout (this actually runs now) ──────────────────
        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            drainer.interrupt();
            String tail = tail(outputBuf, 500);
            log.error("[GS][ERROR] {} timed out after {} min. Last output:\n{}",
                label, timeoutMinutes, tail);
            throw new RuntimeException(label + " timed out after " + timeoutMinutes
                + " minutes. Last output: " + tail);
        }

        // Drainer may still be processing last lines — give it a moment
        drainer.join(5_000);

        int exit = process.exitValue();
        String fullOutput = outputBuf.toString();

        if (exit != 0) {
            String tail = tail(outputBuf, 2000);
            log.error("[GS][ERROR] {} failed (exit {}). Output tail:\n{}", label, exit, tail);
            throw new RuntimeException(label + " failed (exit " + exit + "): " + tail);
        }

        log.info("[GS][{}] Completed (exit 0). Lines logged: {}",
            label, fullOutput.lines().count());
    }

    // Updates DB progress from a tqdm line — at most once every 10 s to avoid DB spam
    private void maybePersistTrainingProgress(
            String line,
            GaussianSplat splat,
            int progressStart,
            int progressEnd,
            int totalIterations,
            long[] lastSaveMs) {

        Matcher m = TQDM_ITER.matcher(line);
        if (!m.find()) return;
        try {
            int current = Integer.parseInt(m.group(1));
            int total   = Integer.parseInt(m.group(2));
            // Only act on lines whose total matches the configured iteration count.
            // Spurious matches (e.g. "12/6" in path strings or other tqdm bars)
            // would produce fraction > 1 and push progress past progressEnd.
            if (total <= 0 || total != totalIterations) return;
            double fraction = Math.min(1.0, Math.max(0.0, (double) current / total));
            int progress = progressStart + (int) (fraction * (progressEnd - progressStart));
            splat.setProcessingProgress(progress);
            splat.setCurrentStep(truncate(
                "Entraînement: " + current + "/" + total + " itérations", 500));

            long now = System.currentTimeMillis();
            if (now - lastSaveMs[0] >= 10_000) {
                splatRepository.save(splat);
                lastSaveMs[0] = now;
                log.info("[GS][Training] Progress saved: {}% ({}/{})",
                    progress, current, total);
            }
        } catch (NumberFormatException ignored) {}
    }

    // ── Python command builder ────────────────────────────────────────────────

    private List<String> buildPythonCmd(List<String> args) {
        List<String> cmd = new ArrayList<>();
        String conda = findConda();
        if (conda != null) {
            log.info("[GS] Using conda: {} (env: {})", conda, gsProps.getCondaEnv());
            cmd.add(conda);
            cmd.add("run");
            cmd.add("--no-capture-output");
            cmd.add("-n"); cmd.add(gsProps.getCondaEnv());
            cmd.add("python");
        } else {
            log.info("[GS] conda not found — using python directly: {}",
                gsProps.getPythonExecutable());
            cmd.add(gsProps.getPythonExecutable());
        }
        cmd.addAll(args);
        return cmd;
    }

    private String findConda() {
        String home = System.getProperty("user.home");
        String[] candidates = {
            home + "/anaconda3/Scripts/conda.exe",
            home + "/miniconda3/Scripts/conda.exe",
            home + "/Anaconda3/Scripts/conda.exe",
            home + "/Miniconda3/Scripts/conda.exe",
            "conda"
        };
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "--version").start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    new String(p.getInputStream().readAllBytes()); // drain
                    return c;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateStatus(GaussianSplat splat, String status, int progress, String step) {
        splat.setStatus(status);
        splat.setProcessingProgress(progress);
        splat.setCurrentStep(truncate(step, 500));   // TEXT column but guard anyway
        log.info("[GS] Saving status: {} {}% — {}", status, progress, step);
        splatRepository.save(splat);
        log.info("[GS] Status saved OK");
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private long countFiles(Path dir, String suffix) throws IOException {
        try (var s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(suffix)).count();
        }
    }

    private static String tail(StringBuilder sb, int maxChars) {
        synchronized (sb) {
            String s = sb.toString();
            return s.length() > maxChars ? s.substring(s.length() - maxChars) : s;
        }
    }

    private void deleteSplatFiles(GaussianSplat splat) {
        if (splat.getWorkDir() == null) return;
        Path dir = Path.of(splat.getWorkDir());
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException e) {
            log.warn("[GS] Could not delete dir {}: {}", dir, e.getMessage());
        }
    }
}
