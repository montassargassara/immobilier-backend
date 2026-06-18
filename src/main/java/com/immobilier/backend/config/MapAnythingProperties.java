package com.immobilier.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.mapanything")
public class MapAnythingProperties {

    /** Absolute path to the local map-anything repository clone. */
    private String repoPath = "C:/Users/MontassarGassara/Desktop/ProjectPFE/map-anything";

    /** Conda environment name that has MapAnything + PyTorch installed. */
    private String condaEnv = "mapanything";

    /** Target number of frames to extract from the input video. */
    private int targetFrames = 40;

    /**
     * Max image size (kept for reference; no longer forwarded to the script).
     * The script's default is used instead.
     */
    private int maxImageSize = 512;

    /**
     * Timeout in minutes for the MapAnything inference step.
     * The first run downloads the ~4 GB Apache checkpoint from HuggingFace,
     * which can easily take 30–60 min on a slow connection before inference starts.
     * Set higher if the download repeatedly times out.
     */
    private int timeoutMinutes = 120;

    /** Working directory for reconstruction jobs. */
    private String workDir = "storage/mapanything";
}
