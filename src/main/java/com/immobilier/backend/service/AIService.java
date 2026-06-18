package com.immobilier.backend.service;

import com.immobilier.backend.config.AIServiceProperties;
import com.immobilier.backend.dto.AIPriceRequest;
import com.immobilier.backend.dto.AIPriceResponse;
import com.immobilier.backend.dto.AIRentalPriceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final RestTemplate restTemplate;
    private final AIServiceProperties aiProps;

    public AIService(RestTemplate restTemplate, AIServiceProperties aiProps) {
        this.restTemplate = restTemplate;
        this.aiProps = aiProps;
    }

    public AIPriceResponse predictPrice(AIPriceRequest req) {
        String url = aiProps.getUrl() + "/api/ai/predict-price";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AIPriceRequest> entity = new HttpEntity<>(req, headers);
        try {
            ResponseEntity<AIPriceResponse> response =
                    restTemplate.postForEntity(url, entity, AIPriceResponse.class);
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("AI service unreachable at {}: {}", url, e.getMessage());
            throw new RuntimeException("Le service IA est temporairement indisponible. Veuillez réessayer dans quelques instants.");
        } catch (Exception e) {
            log.error("AI price prediction failed: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de l'estimation du prix.");
        }
    }

    public AIRentalPriceResponse predictRental(AIPriceRequest req) {
        String url = aiProps.getUrl() + "/api/ai/predict-rental";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AIPriceRequest> entity = new HttpEntity<>(req, headers);
        try {
            ResponseEntity<AIRentalPriceResponse> response =
                    restTemplate.postForEntity(url, entity, AIRentalPriceResponse.class);
            return response.getBody();
        } catch (ResourceAccessException e) {
            log.error("AI service unreachable: {}", e.getMessage());
            throw new RuntimeException("Le service IA est temporairement indisponible.");
        } catch (Exception e) {
            log.error("AI rental prediction failed: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de l'estimation du loyer.");
        }
    }

    public boolean isHealthy() {
        try {
            ResponseEntity<String> r = restTemplate.getForEntity(aiProps.getUrl() + "/health", String.class);
            return r.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
