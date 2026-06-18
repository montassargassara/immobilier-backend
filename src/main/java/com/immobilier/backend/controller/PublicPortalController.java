package com.immobilier.backend.controller;

import com.immobilier.backend.dto.PublicPropertyCardDTO;
import com.immobilier.backend.service.PublicPortalService;
import com.immobilier.backend.service.PublicPortalService.PublicSearchFilters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Public visitor-facing portal API. All endpoints are unauthenticated.
 * Data exposed here is intentionally minimal — no commission, no affiliate flags,
 * no internal sharing metadata.
 */
@Slf4j
@RestController
@RequestMapping("/api/properties/public/portal")
@RequiredArgsConstructor
public class PublicPortalController {

    private final PublicPortalService publicPortalService;

    @GetMapping("/vente")
    public ResponseEntity<List<PublicPropertyCardDTO>> listForSale(PublicSearchFilters filters) {
        return ResponseEntity.ok(publicPortalService.listForSale(filters));
    }

    @GetMapping("/location")
    public ResponseEntity<List<PublicPropertyCardDTO>> listForRent(PublicSearchFilters filters) {
        return ResponseEntity.ok(publicPortalService.listForRent(filters));
    }

    @GetMapping("/featured/vente")
    public ResponseEntity<List<PublicPropertyCardDTO>> featuredVente(
            @RequestParam(defaultValue = "6") int limit) {
        return ResponseEntity.ok(publicPortalService.featuredForSale(limit));
    }

    @GetMapping("/featured/location")
    public ResponseEntity<List<PublicPropertyCardDTO>> featuredLocation(
            @RequestParam(defaultValue = "6") int limit) {
        return ResponseEntity.ok(publicPortalService.featuredForRent(limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(publicPortalService.getDetail(id));
        } catch (RuntimeException e) {
            log.warn("Public detail not available for {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/similar")
    public ResponseEntity<List<PublicPropertyCardDTO>> similar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "4") int limit) {
        return ResponseEntity.ok(publicPortalService.similarTo(id, limit));
    }

    @GetMapping("/facets/countries")
    public ResponseEntity<List<String>> countries() {
        return ResponseEntity.ok(publicPortalService.distinctCountries());
    }

    @GetMapping("/facets/cities")
    public ResponseEntity<List<String>> cities(@RequestParam(required = false) String country) {
        return ResponseEntity.ok(publicPortalService.distinctCities(country));
    }

    @GetMapping("/facets/types")
    public ResponseEntity<List<String>> types() {
        return ResponseEntity.ok(publicPortalService.distinctTypes());
    }
}
