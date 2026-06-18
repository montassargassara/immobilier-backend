package com.immobilier.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Runs once at startup to:
 *  1. Patch the DB schema — make BLOB columns nullable so filesystem-based rows can be inserted.
 *     (Hibernate ddl-auto=update never removes NOT NULL from existing columns.)
 *  2. Create the stable storage directory tree.
 *  3. Log FFmpeg availability.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Value("${app.storage.path:storage}")
    private String storagePath;

    @Value("${app.virtual-tour.upload-dir:storage/virtual-tours}")
    private String virtualTourDir;

    @Value("${app.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Override
    public void run(ApplicationArguments args) {
        migrateSchema();
        ensureDirectories();
        checkFFmpeg();
    }

    // ── Schema migration ─────────────────────────────────────────────────────

    private void migrateSchema() {
        log.info("=== StorageMigrationRunner: applying schema patches ===");

        // videos — make BLOB and core columns nullable so filesystem-based rows are accepted
        runAlter("videos", "file_data", "ALTER TABLE videos MODIFY COLUMN file_data LONGBLOB NULL");
        runAlter("videos", "file_name", "ALTER TABLE videos MODIFY COLUMN file_name VARCHAR(255) NULL");
        runAlter("videos", "file_type", "ALTER TABLE videos MODIFY COLUMN file_type VARCHAR(100) NULL");
        runAlter("videos", "file_size", "ALTER TABLE videos MODIFY COLUMN file_size BIGINT NULL");

        runIfColumnMissing("videos", "file_path",
                "ALTER TABLE videos ADD COLUMN file_path VARCHAR(1000) NULL");
        runIfColumnMissing("videos", "original_name",
                "ALTER TABLE videos ADD COLUMN original_name VARCHAR(255) NULL");
        runIfColumnMissing("videos", "mime_type",
                "ALTER TABLE videos ADD COLUMN mime_type VARCHAR(100) NULL");

        // models_3d
        runAlter("models_3d", "file_data", "ALTER TABLE models_3d MODIFY COLUMN file_data LONGBLOB NULL");
        runAlter("models_3d", "file_name", "ALTER TABLE models_3d MODIFY COLUMN file_name VARCHAR(255) NULL");
        runAlter("models_3d", "file_type", "ALTER TABLE models_3d MODIFY COLUMN file_type VARCHAR(100) NULL");
        runAlter("models_3d", "file_size", "ALTER TABLE models_3d MODIFY COLUMN file_size BIGINT NULL");

        runIfColumnMissing("models_3d", "model_path",
                "ALTER TABLE models_3d ADD COLUMN model_path VARCHAR(1000) NULL");
        runIfColumnMissing("models_3d", "preview_image",
                "ALTER TABLE models_3d ADD COLUMN preview_image VARCHAR(500) NULL");
        runIfColumnMissing("models_3d", "status",
                "ALTER TABLE models_3d ADD COLUMN status VARCHAR(50) NULL");

        // virtual_tours — cross-table links added in the previous session
        runIfColumnMissing("virtual_tours", "source_video_id",
                "ALTER TABLE virtual_tours ADD COLUMN source_video_id BIGINT NULL");
        runIfColumnMissing("virtual_tours", "model3d_id",
                "ALTER TABLE virtual_tours ADD COLUMN model3d_id BIGINT NULL");

        log.info("=== StorageMigrationRunner: schema patches complete ===");
    }

    private void runAlter(String table, String column, String sql) {
        if (sql == null) return;
        try {
            jdbc.execute(sql);
            log.info("  ✓ {}.{} → nullable", table, column);
        } catch (Exception e) {
            log.debug("  ~ {}.{} alter skipped (already correct): {}", table, column, e.getMessage());
        }
    }

    private void runIfColumnMissing(String table, String column, String sql) {
        try {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Long.class, table, column);
            if (count != null && count == 0 && sql != null) {
                jdbc.execute(sql);
                log.info("  ✓ {}.{} column added", table, column);
            } else {
                log.debug("  ~ {}.{} already exists", table, column);
            }
        } catch (Exception e) {
            log.warn("  ⚠ Could not check/add {}.{}: {}", table, column, e.getMessage());
        }
    }

    // ── Directory setup ──────────────────────────────────────────────────────

    private void ensureDirectories() {
        try {
            Path tourDir = Path.of(virtualTourDir).toAbsolutePath();
            Files.createDirectories(tourDir);
            log.info("=== Storage directories ===");
            log.info("  Base    : {}", Path.of(storagePath).toAbsolutePath());
            log.info("  Tours   : {}", tourDir);
        } catch (IOException e) {
            log.error("  ✗ Failed to create storage directories: {}", e.getMessage(), e);
        }
    }

    // ── FFmpeg check ─────────────────────────────────────────────────────────

    private void checkFFmpeg() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            if (done && p.exitValue() == 0) {
                String firstLine = out.lines().findFirst().orElse("unknown version");
                log.info("=== FFmpeg OK: {} ===", firstLine.trim());
            } else {
                log.warn("=== FFmpeg NOT found at '{}' — virtual tour generation will fail ===", ffmpegPath);
                log.warn("    Install FFmpeg and add it to PATH, or set app.ffmpeg.path in application.properties");
            }
        } catch (Exception e) {
            log.warn("=== FFmpeg check error ('{}'): {} ===", ffmpegPath, e.getMessage());
        }
    }
}
