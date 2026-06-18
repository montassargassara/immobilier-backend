package com.immobilier.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class TourVideoProcessingService {

    @Value("${app.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${app.ffprobe.path:ffprobe}")
    private String ffprobePath;

    private static final int PROCESS_TIMEOUT_MINUTES = 10;

    public boolean isFFmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(10, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            log.warn("FFmpeg not found at '{}': {}", ffmpegPath, e.getMessage());
            return false;
        }
    }

    public double getVideoDuration(Path videoPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                ffprobePath,
                "-v", "quiet",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor(30, TimeUnit.SECONDS);
            return Double.parseDouble(out);
        } catch (Exception e) {
            log.warn("Could not get video duration: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Extract scene frames from video using FFmpeg.
     * Falls back to uniform extraction if scene-detection yields < 3 frames.
     */
    public List<Path> extractSceneFrames(Path videoPath, Path outputDir, int maxScenes)
            throws IOException, InterruptedException {
        Files.createDirectories(outputDir);

        double duration = getVideoDuration(videoPath);
        if (duration <= 0) duration = 60.0;

        List<Path> scenes = runSceneDetection(videoPath, outputDir, maxScenes, 0.30);

        if (scenes.size() < 3) {
            log.info("Scene detection yielded {} frames — falling back to uniform extraction", scenes.size());
            scenes = runUniformExtraction(videoPath, outputDir, maxScenes, duration);
        }

        generateThumbnails(outputDir, scenes);
        log.info("Extracted {} scene frames from video (duration={}s)", scenes.size(), duration);
        return scenes;
    }

    private List<Path> runSceneDetection(Path videoPath, Path outputDir, int maxScenes, double threshold)
            throws IOException, InterruptedException {
        Path outPattern = outputDir.resolve("scene_%03d.jpg");
        String selectFilter = "select=gt(scene\\," + threshold + "),setpts=N/TB";

        List<String> cmd = Arrays.asList(
            ffmpegPath,
            "-i", videoPath.toAbsolutePath().toString(),
            "-vf", selectFilter,
            "-vsync", "vfr",
            "-q:v", "2",
            "-vframes", String.valueOf(maxScenes),
            "-y",
            outPattern.toAbsolutePath().toString()
        );

        return runAndCollect(cmd, outputDir, "scene_");
    }

    private List<Path> runUniformExtraction(Path videoPath, Path outputDir, int maxScenes, double duration)
            throws IOException, InterruptedException {
        // Remove any partial files from the failed scene detection pass
        try (Stream<Path> s = Files.list(outputDir)) {
            s.filter(p -> p.getFileName().toString().startsWith("scene_"))
             .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }

        double interval = Math.max(1.0, duration / maxScenes);
        Path outPattern = outputDir.resolve("scene_%03d.jpg");

        List<String> cmd = Arrays.asList(
            ffmpegPath,
            "-i", videoPath.toAbsolutePath().toString(),
            "-vf", String.format(Locale.US, "fps=1/%.2f", interval),
            "-q:v", "2",
            "-vframes", String.valueOf(maxScenes),
            "-y",
            outPattern.toAbsolutePath().toString()
        );

        return runAndCollect(cmd, outputDir, "scene_");
    }

    private List<Path> runAndCollect(List<String> cmd, Path outputDir, String prefix)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("FFmpeg timed out after " + PROCESS_TIMEOUT_MINUTES + " minutes");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.warn("FFmpeg exited with code {}: {}", exitCode, output);
        }

        try (Stream<Path> stream = Files.list(outputDir)) {
            return stream
                .filter(p -> p.getFileName().toString().startsWith(prefix)
                          && p.getFileName().toString().endsWith(".jpg"))
                .sorted()
                .collect(Collectors.toList());
        }
    }

    private void generateThumbnails(Path outputDir, List<Path> scenes) {
        for (Path scene : scenes) {
            String sceneName = scene.getFileName().toString();
            String thumbName = sceneName.replace("scene_", "thumb_");
            Path thumbPath = outputDir.resolve(thumbName);
            try {
                List<String> cmd = Arrays.asList(
                    ffmpegPath,
                    "-i", scene.toAbsolutePath().toString(),
                    "-vf", "scale=320:180:force_original_aspect_ratio=decrease,pad=320:180:(ow-iw)/2:(oh-ih)/2",
                    "-q:v", "5",
                    "-y",
                    thumbPath.toAbsolutePath().toString()
                );
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.getInputStream().readAllBytes();
                p.waitFor(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Thumbnail generation failed for {}: {}", sceneName, e.getMessage());
            }
        }
    }

    public void deleteTourFiles(Path tourDir) {
        if (tourDir == null || !Files.exists(tourDir)) return;
        try (Stream<Path> stream = Files.walk(tourDir)) {
            stream.sorted(Comparator.reverseOrder())
                  .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException e) {
            log.warn("Could not delete tour directory {}: {}", tourDir, e.getMessage());
        }
    }
}
