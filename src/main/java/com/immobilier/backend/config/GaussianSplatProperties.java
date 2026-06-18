package com.immobilier.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.gaussian")
public class GaussianSplatProperties {
    private String workDir = "storage/gaussian";
    private String gsPath = "C:/Users/MontassarGassara/Desktop/ProjectPFE/gaussian-splatting";
    private String colmapExecutable = "C:/gs/colmap-x64-windows-cuda/COLMAP.bat";
    private String pythonExecutable = "python";
    private String condaEnv = "gs";
    /** Default number of Gaussian Splatting training iterations. */
    private int iterations = 30000;
    /** Target number of frames to extract from the video before COLMAP. */
    private int targetFrames = 200;
    /** Laplacian-variance threshold below which a frame is considered blurry and discarded. */
    private double blurThreshold = 100.0;
}
