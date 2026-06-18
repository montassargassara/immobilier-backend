package com.immobilier.backend.service;

import com.immobilier.backend.dto.VirtualTourDTO;
import com.immobilier.backend.dto.VirtualTourSceneDTO;
import com.immobilier.backend.entity.Model3D;
import com.immobilier.backend.entity.Video;
import com.immobilier.backend.entity.VirtualTour;
import com.immobilier.backend.entity.VirtualTourScene;
import com.immobilier.backend.repository.Model3DRepository;
import com.immobilier.backend.repository.VideoRepository;
import com.immobilier.backend.repository.VirtualTourRepository;
import com.immobilier.backend.repository.VirtualTourSceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualTourService {

    private final VirtualTourRepository tourRepository;
    private final VirtualTourSceneRepository sceneRepository;
    private final TourVideoProcessingService videoProcessor;
    private final VideoRepository videoRepository;
    private final Model3DRepository model3DRepository;

    // Self-reference through the Spring proxy so @Async on processAsync() is honoured.
    // @Lazy breaks the circular dependency during context startup.
    @Autowired @Lazy
    private VirtualTourService self;

    @Value("${app.virtual-tour.upload-dir:storage/virtual-tours}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.virtual-tour.max-scenes:20}")
    private int maxScenes;

    // ── By-ID read (standalone tours have no propertyId) ─────────────────────

    public VirtualTourDTO getTourById(Long tourId) {
        try {
            VirtualTour tour = tourRepository.findById(tourId).orElse(null);
            if (tour == null) return VirtualTourDTO.notCreated();
            List<VirtualTourSceneDTO> scenes = buildSceneDTOs(tour);
            return VirtualTourDTO.from(tour, scenes);
        } catch (Exception e) {
            log.error("Error loading tour {}: {}", tourId, e.getMessage(), e);
            return VirtualTourDTO.notCreated();
        }
    }

    public VirtualTourDTO getTourStatusById(Long tourId) {
        try {
            VirtualTour tour = tourRepository.findById(tourId).orElse(null);
            if (tour == null) return VirtualTourDTO.notCreated();
            VirtualTourDTO dto = new VirtualTourDTO();
            dto.setId(tour.getId());
            dto.setPropertyId(tour.getPropertyId());
            dto.setStatus(tour.getStatus() != null ? tour.getStatus() : "PENDING");
            dto.setSceneCount(tour.getSceneCount() != null ? tour.getSceneCount() : 0);
            dto.setProcessingProgress(tour.getProcessingProgress() != null ? tour.getProcessingProgress() : 0);
            dto.setErrorMessage(tour.getErrorMessage());
            dto.setIs360(tour.getIs360());
            dto.setVideoDurationSeconds(tour.getVideoDurationSeconds());
            dto.setCreatedAt(tour.getCreatedAt());
            dto.setUpdatedAt(tour.getUpdatedAt());
            dto.setScenes(List.of());
            dto.setSourceVideoId(tour.getSourceVideoId());
            dto.setModel3dId(tour.getModel3dId());
            return dto;
        } catch (Exception e) {
            log.error("Error fetching status for tour {}: {}", tourId, e.getMessage(), e);
            return VirtualTourDTO.notCreated();
        }
    }

    // ── Public & admin read ──────────────────────────────────────────────────

    public VirtualTourDTO getTourByPropertyId(Long propertyId) {
        log.info("getTourByPropertyId: propertyId={}", propertyId);
        try {
            List<VirtualTour> rows = tourRepository.findAllByPropertyIdDesc(propertyId);
            if (rows.isEmpty()) {
                log.info("No tour found for property {}", propertyId);
                return VirtualTourDTO.notCreated();
            }
            VirtualTour tour = rows.get(0);
            log.info("Tour found: id={} status={} scenes={}", tour.getId(), tour.getStatus(), tour.getSceneCount());
            List<VirtualTourSceneDTO> scenes = buildSceneDTOs(tour);
            return VirtualTourDTO.from(tour, scenes);
        } catch (Exception e) {
            log.error("Error loading tour for property {}: {} [{}]", propertyId, e.getMessage(), e.getClass().getSimpleName(), e);
            return VirtualTourDTO.notCreated();
        }
    }

    // No @Transactional — each Spring Data save() in the repo has its own REQUIRED
    // transaction, and a plain read does not need a wrapping transaction context.
    // Removing it eliminates any risk of the AOP proxy's commit phase throwing
    // after the method body returns and propagating past the controller's catch.
    public VirtualTourDTO getTourStatus(Long propertyId) {
        log.info("[STATUS] Entering getTourStatus for property {}", propertyId);
        try {
            List<VirtualTour> rows = tourRepository.findAllByPropertyIdDesc(propertyId);
            log.info("[STATUS] Query returned {} rows for property {}", rows.size(), propertyId);
            if (rows.isEmpty()) {
                return VirtualTourDTO.notCreated();
            }
            VirtualTour tour = rows.get(0);
            log.info("[STATUS] Tour id={} status={} progress={}%",
                tour.getId(), tour.getStatus(), tour.getProcessingProgress());
            VirtualTourDTO dto = new VirtualTourDTO();
            dto.setId(tour.getId());
            dto.setPropertyId(tour.getPropertyId());
            dto.setStatus(tour.getStatus() != null ? tour.getStatus() : "PENDING");
            dto.setSceneCount(tour.getSceneCount() != null ? tour.getSceneCount() : 0);
            dto.setProcessingProgress(tour.getProcessingProgress() != null ? tour.getProcessingProgress() : 0);
            dto.setErrorMessage(tour.getErrorMessage());
            dto.setIs360(tour.getIs360());
            dto.setVideoDurationSeconds(tour.getVideoDurationSeconds());
            dto.setCreatedAt(tour.getCreatedAt());
            dto.setUpdatedAt(tour.getUpdatedAt());
            dto.setScenes(List.of());
            dto.setSourceVideoId(tour.getSourceVideoId());
            dto.setModel3dId(tour.getModel3dId());
            log.info("[STATUS] Returning DTO status={} for property {}", dto.getStatus(), propertyId);
            return dto;
        } catch (Exception e) {
            log.error("[STATUS] Exception in getTourStatus for property {}: {} [{}]",
                propertyId, e.getMessage(), e.getClass().getName(), e);
            return VirtualTourDTO.notCreated();
        }
    }

    // ── Initiate generation ──────────────────────────────────────────────────

    @Transactional
    public VirtualTourDTO initiateGeneration(Long propertyId, MultipartFile videoFile, boolean is360)
            throws IOException {

        // Validate FFmpeg before touching the filesystem
        if (!videoProcessor.isFFmpegAvailable()) {
            throw new RuntimeException(
                "FFmpeg is not installed or not in PATH. Please install FFmpeg to use virtual tour generation.");
        }

        // Cancel any existing tour for this property — use list query to survive duplicate rows
        tourRepository.findAllByPropertyIdDesc(propertyId).forEach(existing -> {
            sceneRepository.deleteByTourId(existing.getId());
            deleteTourFiles(existing);
            tourRepository.delete(existing);
        });

        // ── Resolve storage directory and create it ──────────────────────────
        // Using an absolute resolution ensures we never write inside Tomcat temp.
        // Path.of(uploadDir) resolves relative to the JVM working directory (backend/).
        Path tourDir = resolvePropertyDir(propertyId);
        Path videosDir = tourDir.resolve("videos");
        Files.createDirectories(videosDir);

        // ── Save video to stable storage using InputStream (not transferTo) ──
        // transferTo() is a file-move on disk — it fails when Tomcat temp gets
        // cleaned up between the multipart parse and our handler. Reading the
        // InputStream directly bypasses the temp file entirely.
        String originalName = videoFile.getOriginalFilename() != null
            ? videoFile.getOriginalFilename() : "input.mp4";
        String storedName = "source_" + System.currentTimeMillis() + "_" + sanitize(originalName);
        Path videoPath = videosDir.resolve(storedName);

        try (InputStream is = videoFile.getInputStream()) {
            Files.copy(is, videoPath, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("Video saved to stable storage: {}", videoPath.toAbsolutePath());

        // ── Persist Video record in videos table ─────────────────────────────
        Video videoRecord = new Video();
        videoRecord.setPropertyId(propertyId);
        videoRecord.setFileName(storedName);
        videoRecord.setOriginalName(originalName);
        videoRecord.setFilePath(videoPath.toAbsolutePath().toString());
        String mimeType = videoFile.getContentType() != null ? videoFile.getContentType() : "video/mp4";
        videoRecord.setMimeType(mimeType);
        videoRecord.setFileType(mimeType);
        videoRecord.setFileSize(videoFile.getSize());
        videoRecord.setFileData(null); // filesystem-based — no BLOB
        videoRecord.setIsPrimary(false);
        videoRecord.setSortOrder(0);
        Video savedVideo = videoRepository.save(videoRecord);
        log.info("Video record saved to DB: id={}", savedVideo.getId());

        // ── Get video duration ───────────────────────────────────────────────
        double duration = videoProcessor.getVideoDuration(videoPath);

        // ── Create tour record in PENDING state ──────────────────────────────
        VirtualTour tour = new VirtualTour();
        tour.setPropertyId(propertyId);
        tour.setStatus("PENDING");
        tour.setSourceVideoPath(videoPath.toAbsolutePath().toString());
        tour.setTourDir(tourDir.toAbsolutePath().toString());
        tour.setIs360(is360);
        tour.setVideoDurationSeconds(duration > 0 ? duration : null);
        tour.setSourceVideoId(savedVideo.getId());
        tour = tourRepository.save(tour);

        // Kick off async FFmpeg processing — must call through proxy (self) so @Async is honoured.
        // A direct this.processAsync() call bypasses CGLIB and runs synchronously inside this
        // @Transactional boundary, causing TransactionSystemException when the inner save fails.
        self.processAsync(tour.getId(), videoPath, tourDir, is360);

        return VirtualTourDTO.from(tour, List.of());
    }

    // ── Standalone generation (no propertyId required) ────────────────────────

    @Transactional
    public VirtualTourDTO initiateStandaloneGeneration(MultipartFile videoFile, boolean is360)
            throws IOException {

        if (!videoProcessor.isFFmpegAvailable()) {
            throw new RuntimeException(
                "FFmpeg is not installed or not in PATH. Please install FFmpeg to use virtual tour generation.");
        }

        // Persist the tour first to obtain its auto-generated ID, then use that
        // ID as the filesystem directory name (avoids a chicken-and-egg problem).
        VirtualTour tour = new VirtualTour();
        tour.setPropertyId(null);
        tour.setStatus("PENDING");
        tour.setIs360(is360);
        tour = tourRepository.save(tour);

        Path tourDir = Path.of(uploadDir, "standalone", String.valueOf(tour.getId())).toAbsolutePath();
        Path videosDir = tourDir.resolve("videos");
        Files.createDirectories(videosDir);

        String originalName = videoFile.getOriginalFilename() != null
            ? videoFile.getOriginalFilename() : "input.mp4";
        String storedName = "source_" + System.currentTimeMillis() + "_" + sanitize(originalName);
        Path videoPath = videosDir.resolve(storedName);

        try (InputStream is = videoFile.getInputStream()) {
            Files.copy(is, videoPath, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("Standalone tour video saved: {}", videoPath.toAbsolutePath());

        double duration = videoProcessor.getVideoDuration(videoPath);

        tour.setSourceVideoPath(videoPath.toAbsolutePath().toString());
        tour.setTourDir(tourDir.toAbsolutePath().toString());
        tour.setVideoDurationSeconds(duration > 0 ? duration : null);
        tour = tourRepository.save(tour);

        self.processAsync(tour.getId(), videoPath, tourDir, is360);

        return VirtualTourDTO.from(tour, List.of());
    }

    // ── Async FFmpeg processing ───────────────────────────────────────────────

    @Async
    public void processAsync(Long tourId, Path videoPath, Path tourDir, boolean is360) {
        VirtualTour tour = tourRepository.findById(tourId).orElse(null);
        if (tour == null) return;

        try {
            tour.setStatus("PROCESSING");
            tour.setProcessingProgress(5);
            tourRepository.save(tour);

            Path scenesDir = tourDir.resolve("scenes");
            Files.createDirectories(scenesDir);

            tour.setProcessingProgress(10);
            tourRepository.save(tour);

            // Extract frames with FFmpeg
            List<Path> sceneFiles = videoProcessor.extractSceneFrames(videoPath, scenesDir, maxScenes);

            if (sceneFiles.isEmpty()) {
                throw new RuntimeException(
                    "FFmpeg produced no scene images. Check that the video file is valid and FFmpeg is working.");
            }

            tour.setProcessingProgress(70);
            tourRepository.save(tour);

            // Persist scene metadata
            List<VirtualTourScene> scenes = buildSceneEntities(tourId, sceneFiles);
            sceneRepository.saveAll(scenes);

            tour.setProcessingProgress(90);
            tourRepository.save(tour);

            // ── Archive any previous virtual-tour Model3D rows ────────────────
            if (tour.getPropertyId() != null) {
                model3DRepository.archiveVirtualTourModelsByPropertyId(tour.getPropertyId());
            }

            // ── Persist Model3D record in models_3d table ─────────────────────
            String previewFilename = sceneFiles.isEmpty()
                ? null : sceneFiles.get(0).getFileName().toString();

            Model3D model = new Model3D();
            model.setPropertyId(tour.getPropertyId());
            model.setFileName("virtual_tour_" + (tour.getPropertyId() != null ? tour.getPropertyId() : "standalone_" + tour.getId()));
            model.setFileType("virtual_tour");
            model.setFileSize(0L);
            model.setFileData(null); // filesystem-based
            model.setFormat("virtual_tour");
            model.setModelPath(tourDir.toAbsolutePath().toString());
            model.setPreviewImage(previewFilename);
            model.setStatus("COMPLETED");
            model.setIsActive(true);
            model.setDescription("Visite virtuelle générée automatiquement — " + scenes.size() + " scènes");
            Model3D savedModel = model3DRepository.save(model);
            log.info("Model3D record saved to DB: id={}", savedModel.getId());

            // ── Finalize tour record ──────────────────────────────────────────
            tour.setStatus("COMPLETED");
            tour.setSceneCount(scenes.size());
            tour.setProcessingProgress(100);
            tour.setErrorMessage(null);
            tour.setModel3dId(savedModel.getId());
            tourRepository.save(tour);

            log.info("Virtual tour COMPLETED tourId={} propertyId={} — {} scenes, model3dId={}",
                tourId, tour.getPropertyId(), scenes.size(), savedModel.getId());

        } catch (Exception e) {
            log.error("Virtual tour generation FAILED for tour {}: {}", tourId, e.getMessage(), e);
            tour.setStatus("FAILED");
            tour.setErrorMessage(e.getMessage());
            tour.setProcessingProgress(0);
            tourRepository.save(tour);
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteTour(Long propertyId) {
        List<VirtualTour> tours = tourRepository.findAllByPropertyIdDesc(propertyId);
        if (tours.isEmpty()) return;

        // Wipe all rows (duplicate rows from failed generations are cleaned up here too)
        for (VirtualTour tour : tours) {
            sceneRepository.deleteByTourId(tour.getId());

            if (tour.getSourceVideoId() != null) {
                videoRepository.findById(tour.getSourceVideoId()).ifPresent(v -> {
                    v.setIsActive(false);
                    videoRepository.save(v);
                });
            }
            if (tour.getModel3dId() != null) {
                model3DRepository.findById(tour.getModel3dId()).ifPresent(m -> {
                    m.setIsActive(false);
                    model3DRepository.save(m);
                });
            }

            deleteTourFiles(tour);
            tourRepository.delete(tour);
        }
    }

    // ── Scene image serving ───────────────────────────────────────────────────

    public byte[] getSceneImageBytes(Long tourId, String filename) throws IOException {
        VirtualTour tour = tourRepository.findById(tourId)
            .orElseThrow(() -> new RuntimeException("Tour not found: " + tourId));

        Path safeDir = Path.of(tour.getTourDir()).resolve("scenes").toAbsolutePath().normalize();
        Path imagePath = safeDir.resolve(sanitize(filename)).normalize();

        // Path traversal guard
        if (!imagePath.startsWith(safeDir)) {
            throw new RuntimeException("Access denied: " + filename);
        }
        if (!Files.exists(imagePath)) {
            throw new RuntimeException("Scene image not found: " + filename);
        }
        return Files.readAllBytes(imagePath);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<VirtualTourScene> buildSceneEntities(Long tourId, List<Path> sceneFiles) {
        List<VirtualTourScene> entities = new ArrayList<>();
        for (int i = 0; i < sceneFiles.size(); i++) {
            Path scenePath = sceneFiles.get(i);
            String sceneName = scenePath.getFileName().toString();
            String thumbName = sceneName.replace("scene_", "thumb_");

            VirtualTourScene scene = new VirtualTourScene();
            scene.setTourId(tourId);
            scene.setSceneIndex(i);
            scene.setSceneName("Scène " + (i + 1));
            scene.setImageFilename(sceneName);

            Path thumbPath = scenePath.getParent().resolve(thumbName);
            if (Files.exists(thumbPath)) {
                scene.setThumbnailFilename(thumbName);
            }

            scene.setIsDefault(i == 0);
            entities.add(scene);
        }
        return entities;
    }

    private List<VirtualTourSceneDTO> buildSceneDTOs(VirtualTour tour) {
        return sceneRepository.findByTourIdOrderBySceneIndexAsc(tour.getId())
            .stream()
            .map(s -> VirtualTourSceneDTO.from(s, tour.getId(), baseUrl))
            .toList();
    }

    private Path resolvePropertyDir(Long propertyId) {
        // Always use the absolute path so it never resolves inside Tomcat temp
        return Path.of(uploadDir, String.valueOf(propertyId)).toAbsolutePath();
    }

    private void deleteTourFiles(VirtualTour tour) {
        if (tour.getTourDir() != null) {
            videoProcessor.deleteTourFiles(Path.of(tour.getTourDir()));
        }
    }

    private String sanitize(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
