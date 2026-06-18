package com.immobilier.backend.config;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import jakarta.servlet.MultipartConfigElement;

@Configuration
public class MultipartConfig {
    
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofGigabytes(1));      // ✅ 1GB
        factory.setMaxRequestSize(DataSize.ofGigabytes(1));  // ✅ 1GB
        factory.setFileSizeThreshold(DataSize.ofKilobytes(2));
        return factory.createMultipartConfig();
    }
}