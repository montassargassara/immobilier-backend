package com.immobilier.backend.controller;

import com.immobilier.backend.dto.AIPriceRequest;
import com.immobilier.backend.dto.AIPriceResponse;
import com.immobilier.backend.dto.AIRentalPriceResponse;
import com.immobilier.backend.service.AIService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/predict-price")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AIPriceResponse> predictPrice(@Valid @RequestBody AIPriceRequest req) {
        AIPriceResponse result = aiService.predictPrice(req);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/predict-rental")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AIRentalPriceResponse> predictRental(@Valid @RequestBody AIPriceRequest req) {
        AIRentalPriceResponse result = aiService.predictRental(req);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> health() {
        boolean healthy = aiService.isHealthy();
        return ResponseEntity.ok(java.util.Map.of("aiServiceOnline", healthy));
    }
}
