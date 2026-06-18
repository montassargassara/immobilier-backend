package com.immobilier.backend.service;

import com.immobilier.backend.config.MapAnythingProperties;
import com.immobilier.backend.dto.MapAnythingJobDTO;
import com.immobilier.backend.entity.MapAnythingJob;
import com.immobilier.backend.entity.Model3D;
import com.immobilier.backend.repository.MapAnythingJobRepository;
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
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapAnythingService {

    private final MapAnythingJobRepository jobRepository;
    private final Model3DRepository model3DRepository;
    private final PropertyRepository propertyRepository;
    private final MapAnythingProperties maProps;

    // Self-reference through the Spring proxy so @Async is honoured on runPipelineAsync.
    @Autowired @Lazy
    private MapAnythingService self;

    @Value("${app.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${app.ffprobe.path:ffprobe}")
    private String ffprobePath;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${file.upload.models-dir:uploads/models}")
    private String modelsDir;

    // Python blur-filter script — cv2 primary, PIL+scipy fallback.
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
            scored.sort(key=lambda x: x[1], reverse=True)

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

    // Python script to extract vertex/mesh count from a GLB via trimesh.
    private static final String GLB_STATS_SCRIPT = """
            import sys
            try:
                import trimesh
                scene = trimesh.load(sys.argv[1], force='scene')
                if isinstance(scene, trimesh.Scene):
                    geoms = list(scene.geometry.values())
                elif hasattr(scene, 'vertices'):
                    geoms = [scene]
                else:
                    geoms = []
                total_vertices = sum(len(g.vertices) for g in geoms if hasattr(g, 'vertices'))
                total_meshes = len(geoms)
                print(f"vertices={total_vertices} meshes={total_meshes}", flush=True)
            except Exception as e:
                print(f"vertices=0 meshes=0 error={e}", flush=True)
            """;

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional
    public MapAnythingJobDTO initiate(Long propertyId, MultipartFile videoFile) throws IOException {
        if (propertyId != null) {
            jobRepository.findAllByPropertyIdOrderByCreatedAtDesc(propertyId).forEach(old -> {
                deleteJobFiles(old);
                jobRepository.delete(old);
            });
        }
        MapAnythingJob job = createAndSave(propertyId, videoFile);
        self.runPipelineAsync(job.getId());
        return MapAnythingJobDTO.from(job, baseUrl);
    }

    public MapAnythingJobDTO getStatusForProperty(Long propertyId) {
        List<MapAnythingJob> rows = jobRepository.findAllByPropertyIdOrderByCreatedAtDesc(propertyId);
        if (rows.isEmpty()) return MapAnythingJobDTO.notCreated();
        return MapAnythingJobDTO.from(rows.get(0), baseUrl);
    }

    /**
     * Returns the GLB path for preview streaming (AWAITING_VALIDATION or ACCEPTED).
     * For AWAITING_VALIDATION: temp path in workDir.
     * For ACCEPTED: permanent path in modelsDir.
     */
    public Path getGlbPath(Long jobId) throws IOException {
        MapAnythingJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("MapAnything job not found: " + jobId));
        if (job.getGlbFilePath() == null)
            throw new RuntimeException("No GLB file recorded for job: " + jobId);
        Path path = Path.of(job.getGlbFilePath());
        if (!Files.exists(path))
            throw new IOException("GLB file missing on disk: " + path);
        return path;
    }

    /**
     * Accept: copy temp GLB to permanent storage, create Model3D, link to property.
     * Returns the updated job DTO (with glbUrl set to the public model URL).
     */
    @Transactional
    public MapAnythingJobDTO acceptJob(Long jobId) throws IOException {
        MapAnythingJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("MapAnything job not found: " + jobId));
        if (!"AWAITING_VALIDATION".equals(job.getStatus()))
            throw new IllegalStateException("Cannot accept: status=" + job.getStatus());
        if (job.getGlbFilePath() == null)
            throw new IllegalStateException("No GLB file path for job: " + jobId);
        if (job.getPropertyId() == null)
            throw new IllegalStateException("Job has no linked property: " + jobId);

        Path src = Path.of(job.getGlbFilePath());
        if (!Files.exists(src))
            throw new IOException("GLB temp file not found: " + src);

        publishGlb(job, src);
        log.info("[MAP] acceptJob done jobId={} model3dId={}", jobId, job.getModel3dId());
        return MapAnythingJobDTO.from(job, baseUrl);
    }

    /**
     * Reject: delete all temporary files (GLB + frames + workDir), set REJECTED.
     * Returns the updated job DTO.
     */
    @Transactional
    public MapAnythingJobDTO rejectJob(Long jobId) {
        MapAnythingJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("MapAnything job not found: " + jobId));
        if (!"AWAITING_VALIDATION".equals(job.getStatus()))
            throw new IllegalStateException("Cannot reject: status=" + job.getStatus());

        deleteJobFiles(job);

        job.setStatus("REJECTED");
        job.setCurrentStep("Rejeté par l'administrateur");
        job.setGlbFilePath(null);
        jobRepository.save(job);
        log.info("[MAP] rejectJob done jobId={}", jobId);
        return MapAnythingJobDTO.from(job, baseUrl);
    }

    // ── Async Pipeline ────────────────────────────────────────────────────────

    @Async
    public void runPipelineAsync(Long jobId) {
        if (jobId == null) {
            log.error("[MAP] runPipelineAsync called with null jobId — aborting");
            return;
        }
        log.info("[MAP] ========== PIPELINE START jobId={} ==========", jobId);
        long pipelineStartMs = System.currentTimeMillis();

        // The parent @Transactional may not have committed yet — retry briefly.
        MapAnythingJob job = null;
        for (int i = 0; i < 20; i++) {
            job = jobRepository.findById(jobId).orElse(null);
            if (job != null) break;
            try { Thread.sleep(200); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (job == null) {
            log.error("[MAP] jobId={} not found after 4 s — aborting", jobId);
            return;
        }

        try {
            Path jobDir    = Path.of(job.getWorkDir());
            Path videoPath = Path.of(job.getSourceVideoPath());
            Path inputDir  = jobDir.resolve("input");

            if (!Files.exists(videoPath))
                throw new RuntimeException("Source video not found: " + videoPath);

            log.info("[MAP] Source video OK: {} ({}B)", videoPath, Files.size(videoPath));

            // ── STEP 1: FFmpeg frame extraction ──────────────────────────────
            log.info("[MAP] ── STEP 1/3: FFmpeg frame extraction (target={}) ──",
                maProps.getTargetFrames());
            updateStatus(job, "PROCESSING", 5, "Extraction des images (FFmpeg)...");
            extractFrames(videoPath, inputDir);

            long frameCount = countFiles(inputDir, ".jpg");
            log.info("[MAP] Frames extracted: {}", frameCount);
            if (frameCount < 3)
                throw new RuntimeException(
                    "FFmpeg produced only " + frameCount + " frames (minimum 3). "
                    + "The video may be too short or corrupt.");
            updateStatus(job, "PROCESSING", 20, frameCount + " images extraites");

            // ── STEP 2: Blur frame filtering ──────────────────────────────────
            log.info("[MAP] ── STEP 2/3: Blur frame filtering ──");
            updateStatus(job, "PROCESSING", 22, "Filtrage des images floues...");
            filterBlurryFrames(inputDir, jobDir);

            long filteredCount = countFiles(inputDir, ".jpg");
            log.info("[MAP] Frames after blur filter: {}", filteredCount);
            if (filteredCount < 3)
                throw new RuntimeException(
                    "Only " + filteredCount + " frames remain after blur filtering. "
                    + "Lower app.mapanything.blur-threshold or use a steadier video.");
            updateStatus(job, "PROCESSING", 25, filteredCount + " images nettes conservées");

            // ── STEP 3: MapAnything inference ─────────────────────────────────
            log.info("[MAP] ── STEP 3/3: MapAnything inference ──");
            updateStatus(job, "PROCESSING", 30, "Reconstruction 3D MapAnything (GPU)...");
            Path glbPath = runMapAnythingInference(jobDir, inputDir);
            log.info("[MAP] GLB produced: {} ({}B)", glbPath, Files.size(glbPath));
            updateStatus(job, "PROCESSING", 85, "Génération GLB terminée — analyse du modèle...");

            // ── Compute stats + set AWAITING_VALIDATION (do NOT publish) ─────
            long generationTimeMs = System.currentTimeMillis() - pipelineStartMs;
            long[] stats = extractGlbStats(glbPath, jobDir);

            job.setGlbFilePath(glbPath.toAbsolutePath().toString());
            job.setGlbFileSize(Files.size(glbPath));
            job.setVertexCount(stats[0]);
            job.setMeshCount((int) stats[1]);
            job.setGenerationTimeMs(generationTimeMs);
            job.setStatus("AWAITING_VALIDATION");
            job.setProcessingProgress(100);
            job.setCurrentStep("Modèle 3D prêt — en attente de validation");
            jobRepository.save(job);

            log.info(
                "[MAP] ========== PIPELINE AWAITING_VALIDATION jobId={} vertices={} meshes={} sizeB={} time={}ms ==========",
                jobId, stats[0], stats[1], Files.size(glbPath), generationTimeMs);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            log.error("[MAP] Pipeline FAILED jobId={}: {}", jobId, msg, e);
            job.setStatus("FAILED");
            job.setErrorMessage(msg);
            job.setCurrentStep(truncate("Erreur : " + msg, 500));
            job.setProcessingProgress(0);
            jobRepository.save(job);
        }
    }

    // ── STEP 1: Frame extraction ──────────────────────────────────────────────

    private void extractFrames(Path videoPath, Path outputDir)
            throws IOException, InterruptedException {
        Files.createDirectories(outputDir);
        double duration = getVideoDuration(videoPath);
        int target = maProps.getTargetFrames();
        double fps = Math.min(5.0, Math.max(0.5, target / Math.max(duration, 1.0)));
        log.info("[MAP] duration={}s target={} fps={}",
            String.format(Locale.US, "%.1f", duration), target,
            String.format(Locale.US, "%.3f", fps));

        List<String> cmd = List.of(
            ffmpegPath, "-y",
            "-i", videoPath.toAbsolutePath().toString(),
            "-vf", "fps=" + String.format(Locale.US, "%.4f", fps),
            "-q:v", "1",
            outputDir.resolve("frame_%04d.jpg").toAbsolutePath().toString()
        );
        runProcess(cmd, "FFmpeg", 10, null, null);
    }

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
            if (!out.isBlank()) return Double.parseDouble(out.replace(',', '.'));
        } catch (Exception e) {
            log.warn("[MAP] ffprobe duration failed: {} — assuming 60 s", e.getMessage());
        }
        return 60.0;
    }

    // ── STEP 2: Blur filter ───────────────────────────────────────────────────

    private void filterBlurryFrames(Path inputDir, Path jobDir)
            throws IOException, InterruptedException {
        Path scriptPath = jobDir.resolve("blur_filter.py");
        Files.writeString(scriptPath, BLUR_FILTER_SCRIPT);
        try {
            // min_keep=20 for MapAnything (target 40 frames → keep best half as floor)
            List<String> cmd = buildPythonCmd(maProps.getCondaEnv(), List.of(
                scriptPath.toAbsolutePath().toString(),
                String.format(Locale.US, "%.4f", 100.0),
                "20",
                inputDir.toAbsolutePath().toString()
            ));
            runProcess(cmd, "BlurFilter", 5, null, null);
        } finally {
            try { Files.deleteIfExists(scriptPath); } catch (IOException ignored) {}
        }
    }

    // ── STEP 3: MapAnything 3D reconstruction ────────────────────────────────

    private Path runMapAnythingInference(Path jobDir, Path inputDir)
            throws IOException, InterruptedException {
        Path repoPath = Path.of(maProps.getRepoPath());
        Path script   = repoPath.resolve("scripts").resolve("demo_images_only_inference.py");
        if (!Files.exists(script))
            throw new RuntimeException(
                "MapAnything inference script not found at: " + script
                + " — check app.mapanything.repo-path in application.properties");

        Path glbOut = jobDir.resolve("model.glb");

        List<String> args = new ArrayList<>(List.of(
            script.toAbsolutePath().toString(),
            "--image_folder", inputDir.toAbsolutePath().toString(),
            "--apache",                              // MANDATORY: Apache 2.0 model (commercial use)
            "--save_glb",
            "--output_path", glbOut.toAbsolutePath().toString(),
            "--headless"
        ));

        List<String> cmd = buildPythonCmd(maProps.getCondaEnv(), args);
        log.info("[MAP] Inference cmd: {}", String.join(" ", cmd));

        Map<String, String> env = new HashMap<>();
        env.put("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True");
        runProcess(cmd, "MapAnything", maProps.getTimeoutMinutes(), env, repoPath.toFile());

        if (!Files.exists(glbOut) || Files.size(glbOut) == 0)
            throw new RuntimeException(
                "Inference finished but no GLB was produced at " + glbOut
                + " — verify that the installed MapAnything version supports --save_glb.");
        return glbOut;
    }

    // ── GLB stats extraction ──────────────────────────────────────────────────

    private long[] extractGlbStats(Path glbPath, Path jobDir) {
        Path scriptPath = jobDir.resolve("glb_stats.py");
        try {
            Files.writeString(scriptPath, GLB_STATS_SCRIPT);
            List<String> cmd = buildPythonCmd(maProps.getCondaEnv(), List.of(
                scriptPath.toAbsolutePath().toString(),
                glbPath.toAbsolutePath().toString()
            ));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(30, TimeUnit.SECONDS);
            log.info("[MAP] GLB stats raw output: {}", out);

            long vertices = 0, meshes = 0;
            for (String token : out.split("\\s+")) {
                if (token.startsWith("vertices=")) {
                    try { vertices = Long.parseLong(token.substring(9)); } catch (NumberFormatException ignored) {}
                } else if (token.startsWith("meshes=")) {
                    try { meshes = Long.parseLong(token.substring(7)); } catch (NumberFormatException ignored) {}
                }
            }
            return new long[]{vertices, meshes};
        } catch (Exception e) {
            log.warn("[MAP] GLB stats extraction failed (non-fatal): {}", e.getMessage());
            return new long[]{0, 0};
        } finally {
            try { Files.deleteIfExists(scriptPath); } catch (IOException ignored) {}
        }
    }

    // ── Publish GLB as Model3D (called by acceptJob) ──────────────────────────

    private void publishGlb(MapAnythingJob job, Path srcGlb) throws IOException {
        Path destDir = Path.of(modelsDir).toAbsolutePath();
        Files.createDirectories(destDir);
        String fileName = "ma_" + job.getId() + ".glb";
        Path dest = destDir.resolve(fileName);
        Files.copy(srcGlb, dest, StandardCopyOption.REPLACE_EXISTING);
        log.info("[MAP] GLB copied to permanent storage: {}", dest);

        // Soft-delete any existing active models for this property
        if (job.getPropertyId() != null) {
            model3DRepository.findByPropertyId(job.getPropertyId())
                .forEach(m -> { m.setIsActive(false); model3DRepository.save(m); });
        }

        Model3D model = new Model3D();
        model.setPropertyId(job.getPropertyId());
        model.setFileName(fileName);
        // Use a MIME type that does NOT contain "gltf" or "glb" so @PrePersist
        // does not override the explicit format we set below.
        model.setFileType("application/octet-stream");
        model.setFileSize(Files.size(dest));
        model.setModelPath(dest.toAbsolutePath().toString());
        model.setFormat("glb");      // must be set BEFORE save so @PrePersist sees the right type
        model.setIsActive(true);
        model.setDescription("Reconstruction 3D MapAnything — validé par l'administrateur");
        Model3D saved = model3DRepository.save(model);

        if (job.getPropertyId() != null) {
            propertyRepository.findById(job.getPropertyId()).ifPresent(p -> {
                p.setMainModel3dId(saved.getId());
                propertyRepository.save(p);
            });
        }

        // Update job to ACCEPTED and link the Model3D
        job.setGlbFilePath(dest.toAbsolutePath().toString());
        job.setGlbFileSize(Files.size(dest));
        job.setModel3dId(saved.getId());
        job.setStatus("ACCEPTED");
        job.setProcessingProgress(100);
        job.setCurrentStep("Modèle 3D GLB publié avec succès");
        jobRepository.save(job);
    }

    // ── Process runner ────────────────────────────────────────────────────────

    private void runProcess(List<String> cmd, String label, int timeoutMinutes,
                            Map<String, String> extraEnv, File workDir)
            throws IOException, InterruptedException {
        log.info("[MAP][{}] CMD: {}", label, String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.environment().put("PYTHONUNBUFFERED", "1");
        if (extraEnv != null) pb.environment().putAll(extraEnv);
        if (workDir != null) pb.directory(workDir);

        Process process = pb.start();
        log.info("[MAP][{}] PID={}", label, process.pid());

        StringBuilder outputBuf = new StringBuilder(4096);
        Thread drainer = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.info("[MAP][{}] {}", label, line);
                    synchronized (outputBuf) { outputBuf.append(line).append('\n'); }
                }
            } catch (IOException e) {
                log.warn("[MAP][{}] drainer error: {}", label, e.getMessage());
            }
        }, "ma-drainer-" + label);
        drainer.setDaemon(true);
        drainer.start();

        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            drainer.interrupt();
            throw new RuntimeException(label + " timed out after " + timeoutMinutes + " min. "
                + "Last output: " + tail(outputBuf, 500));
        }
        drainer.join(5_000);

        String fullOutput = outputBuf.toString();

        // CUDA OOM — give a clear, actionable message
        if (fullOutput.contains("CUDA out of memory")
                || fullOutput.contains("torch.cuda.OutOfMemoryError")
                || fullOutput.contains("OutOfMemoryError")) {
            throw new RuntimeException(
                "GPU hors mémoire (CUDA OOM) lors de la reconstruction MapAnything. "
                + "Solutions : réduire app.mapanything.target-frames (actuel="
                + maProps.getTargetFrames() + ") ou app.mapanything.max-image-size (actuel="
                + maProps.getMaxImageSize() + ") dans application.properties.");
        }

        int exit = process.exitValue();
        if (exit != 0)
            throw new RuntimeException(label + " failed (exit=" + exit + "): "
                + tail(outputBuf, 2000));

        log.info("[MAP][{}] OK (exit 0)", label);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> buildPythonCmd(String condaEnv, List<String> args) {
        List<String> cmd = new ArrayList<>();
        String conda = findConda();
        if (conda != null) {
            log.info("[MAP] conda={} env={}", conda, condaEnv);
            cmd.addAll(List.of(conda, "run", "--no-capture-output", "-n", condaEnv, "python"));
        } else {
            log.warn("[MAP] conda not found — running python directly");
            cmd.add("python");
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
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes(); // drain
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private MapAnythingJob createAndSave(Long propertyId, MultipartFile videoFile)
            throws IOException {
        log.info("[MAP] createAndSave propertyId={} fileSize={}B", propertyId, videoFile.getSize());

        MapAnythingJob job = new MapAnythingJob();
        job.setPropertyId(propertyId);
        job.setStatus("PENDING");
        job = jobRepository.save(job);

        Long id = job.getId();
        if (id == null) throw new IllegalStateException("DB did not return a generated ID");

        Path jobDir  = Path.of(maProps.getWorkDir(), String.valueOf(id)).toAbsolutePath();
        Path inputDir = jobDir.resolve("input");
        Files.createDirectories(inputDir);

        String raw    = videoFile.getOriginalFilename();
        String orig   = (raw != null && !raw.isBlank()) ? raw : "input.mp4";
        String stored = "src_" + System.currentTimeMillis() + "_"
            + orig.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        Path videoPath = jobDir.resolve(stored);

        try (InputStream is = videoFile.getInputStream()) {
            Files.copy(is, videoPath, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("[MAP] Video saved: {} ({}B)", videoPath, Files.size(videoPath));

        job.setSourceVideoPath(videoPath.toAbsolutePath().toString());
        job.setWorkDir(jobDir.toAbsolutePath().toString());
        return jobRepository.save(job);
    }

    private void updateStatus(MapAnythingJob job, String status, int progress, String step) {
        job.setStatus(status);
        job.setProcessingProgress(progress);
        job.setCurrentStep(truncate(step, 500));
        log.info("[MAP] {} {}% — {}", status, progress, step);
        jobRepository.save(job);
    }

    private void deleteJobFiles(MapAnythingJob job) {
        if (job.getWorkDir() == null) return;
        Path dir = Path.of(job.getWorkDir());
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException e) {
            log.warn("[MAP] Could not delete dir {}: {}", dir, e.getMessage());
        }
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

    private static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
