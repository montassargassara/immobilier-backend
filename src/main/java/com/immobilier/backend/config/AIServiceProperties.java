package com.immobilier.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.service")
public class AIServiceProperties {

    private String url = "http://localhost:8000";

    public String getUrl()         { return url; }
    public void   setUrl(String v) { this.url = v; }
}
