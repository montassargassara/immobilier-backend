package com.immobilier.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Self-applying schema migration for the gaussian_splats table.
 *
 * Hibernate's ddl-auto=update never widens existing columns, so any column
 * that was originally created with a narrow type (e.g. VARCHAR(100)) stays
 * narrow even after the entity annotation is corrected.  This runner checks
 * the actual MySQL column type at startup and issues an ALTER only when the
 * column is narrower than required — making the operation fully idempotent.
 *
 * Order(1) ensures it runs before any other ApplicationRunner that might
 * write to the table.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class GaussianSplatSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[GS-Schema] Checking gaussian_splats column types...");

        // Map of column → required MySQL type.
        // These match the @Column annotations on GaussianSplat entity exactly.
        fixColumn("current_step",      "text");
        fixColumn("error_message",     "longtext");
        fixColumn("source_video_path", "varchar(1000)");
        fixColumn("work_dir",          "varchar(1000)");
        fixColumn("ply_file_path",     "varchar(1000)");

        log.info("[GS-Schema] Column check complete.");
    }

    private void fixColumn(String column, String requiredType) {
        try {
            // Query the actual type from information_schema.
            // queryForObject throws EmptyResultDataAccessException (not null)
            // when no row is found — i.e. when the column doesn't exist yet.
            String currentType;
            try {
                currentType = jdbc.queryForObject(
                    "SELECT COLUMN_TYPE " +
                    "FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "  AND TABLE_NAME   = 'gaussian_splats' " +
                    "  AND COLUMN_NAME  = ?",
                    String.class,
                    column
                );
            } catch (EmptyResultDataAccessException missing) {
                // Column doesn't exist yet — Hibernate will create it with the
                // correct type from the entity annotation on this same startup.
                log.info("[GS-Schema] Column '{}' not found — Hibernate will create it.", column);
                return;
            }

            // Normalise both sides to lower-case for comparison
            String current  = currentType.toLowerCase().trim();
            String required = requiredType.toLowerCase().trim();

            if (current.equals(required)) {
                log.info("[GS-Schema] Column '{}' already {} — no change needed.", column, currentType);
                return;
            }

            // The column exists but has a different (narrower) type — ALTER it.
            String sql = "ALTER TABLE gaussian_splats MODIFY COLUMN `" + column + "` " + requiredType;
            log.info("[GS-Schema] Altering '{}': {} → {}   SQL: {}", column, currentType, requiredType, sql);
            jdbc.execute(sql);
            log.info("[GS-Schema] Column '{}' altered successfully.", column);

        } catch (Exception e) {
            // Never crash the application over a schema check failure.
            // The outer catch covers JDBC errors, permission issues, etc.
            log.warn("[GS-Schema] Could not check/fix column '{}': {}", column, e.getMessage());
        }
    }
}
