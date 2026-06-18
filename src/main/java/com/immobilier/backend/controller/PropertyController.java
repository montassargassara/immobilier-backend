package com.immobilier.backend.controller;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.repository.UserRepository;
import com.immobilier.backend.security.CustomUserDetails;
import com.immobilier.backend.security.SecurityUtils;
import com.immobilier.backend.service.PropertyService;
import com.immobilier.backend.service.PropertyShareRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;
    private final PropertyShareRequestService shareRequestService;
    private final SecurityUtils securityUtils;
    private final UserRepository userRepository;  // ✅ AJOUTER CETTE LIGNE

    // ==================== LOCATION / PUBLIC ENDPOINTS ====================

    @GetMapping("/public/countries")
    public ResponseEntity<List<String>> getAllCountries() {
        return ResponseEntity.ok(propertyService.getAllCountries());
    }

    @GetMapping("/public/cities")
    public ResponseEntity<List<String>> getCitiesByCountry(@RequestParam String country) {
        return ResponseEntity.ok(propertyService.getCitiesByCountry(country));
    }

    @GetMapping("/public/regions")
    public ResponseEntity<List<String>> getAllRegions() {
        return ResponseEntity.ok(propertyService.getAllRegions());
    }

    @GetMapping("/public/by-country")
    public ResponseEntity<List<PropertyWithCommissionDTO>> getPropertiesByCountry(@RequestParam String country) {
        return ResponseEntity.ok(propertyService.getPropertiesByCountry(country));
    }

    @GetMapping("/public/by-country-city")
    public ResponseEntity<List<PropertyWithCommissionDTO>> getPropertiesByCountryAndCity(
            @RequestParam String country, @RequestParam String city) {
        return ResponseEntity.ok(propertyService.getPropertiesByCountryAndCity(country, city));
    }

    @GetMapping("/public/by-city")
    public ResponseEntity<List<PropertyWithCommissionDTO>> getPropertiesByCity(@RequestParam String city) {
        return ResponseEntity.ok(propertyService.getPropertiesByCity(city));
    }

    @GetMapping("/public/by-area")
    public ResponseEntity<List<PropertyWithCommissionDTO>> getPropertiesByArea(@RequestParam String area) {
        return ResponseEntity.ok(propertyService.getPropertiesByArea(area));
    }

    @GetMapping("/public/by-regions")
    public ResponseEntity<List<PropertyWithCommissionDTO>> getPropertiesByRegions(@RequestParam List<String> regions) {
        return ResponseEntity.ok(propertyService.getPropertiesByRegions(regions));
    }

    @GetMapping("/public/near-location")
    public ResponseEntity<List<PropertyWithCommissionDTO>> getPropertiesNearLocation(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "10") Double radiusKm) {
        return ResponseEntity.ok(propertyService.getPropertiesNearLocation(lat, lng, radiusKm));
    }

    @GetMapping("/public/by-commission")
    public ResponseEntity<List<PropertyWithCommissionDTO>> getPropertiesByCommission(
            @RequestParam(required = false) Double minCommission,
            @RequestParam(required = false) Double maxCommission) {
        return ResponseEntity.ok(propertyService.getPropertiesByCommissionRange(minCommission, maxCommission));
    }

    @GetMapping("/public/filter")
    public ResponseEntity<List<PropertyWithCommissionDTO>> filterProperties(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Double minCommission,
            @RequestParam(required = false) String propertyType) {
        return ResponseEntity.ok(propertyService.filterProperties(
                country, city, region, minPrice, maxPrice, minCommission, propertyType));
    }

    // Public listing (buyer-facing site – no visibility filter needed)
    @GetMapping("/public")
    public ResponseEntity<?> getAllPropertiesPublic() {
        try {
            return ResponseEntity.ok(propertyService.getAllPropertiesList());
        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/public/full")
    public ResponseEntity<List<PropertyDTO>> getAllPropertiesFullPublic() {
        return ResponseEntity.ok(propertyService.getAllProperties());
    }

    @GetMapping("/public/active")
    public ResponseEntity<List<PropertyDTO>> getActivePropertiesPublic() {
        return ResponseEntity.ok(propertyService.getActiveProperties());
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<?> getPropertyByIdPublic(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(propertyService.getPropertyById(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Propriété non trouvée"));
        }
    }

    @GetMapping("/public/{id}/light")
    public ResponseEntity<?> getPropertyByIdLightPublic(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(propertyService.getPropertyByIdLight(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Propriété non trouvée"));
        }
    }

    @GetMapping("/categories/{category}/allowed-statuses")
    public ResponseEntity<List<String>> getAllowedStatusesForCategory(@PathVariable String category) {
        return ResponseEntity.ok(propertyService.getAllowedStatusesForCategory(category));
    }

    @GetMapping("/{id}/category")
    public ResponseEntity<Map<String, String>> getPropertyCategory(@PathVariable Long id) {
        String category = propertyService.getPropertyCategory(id);
        return ResponseEntity.ok(Map.of("category", category != null ? category : "INCONNU"));
    }

    // ==================== AFFILIATE ====================

    @GetMapping("/affiliate/{affiliateId}")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<List<PropertyWithCommissionDTO>> getPropertiesForAffiliate(
            @PathVariable Long affiliateId) {
        return ResponseEntity.ok(propertyService.getPropertiesForAffiliate(affiliateId));
    }

    // ==================== PROTECTED – ADMIN PANEL ====================
    // All list endpoints now filter by what the current user is allowed to see.

    @GetMapping
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PropertyDTO>> getAllProperties() {
        User currentUser = securityUtils.getCurrentUser();
        return ResponseEntity.ok(propertyService.getAllPropertiesListForUser(currentUser)
                .stream()
                .map(dto -> {
                    // lightweight: reuse the list DTO cast-equivalent — return full DTOs
                    PropertyDTO full = new PropertyDTO();
                    full.setId(dto.getId());
                    full.setTitre(dto.getTitre());
                    full.setDescription(dto.getDescription());
                    full.setType(dto.getType());
                    full.setPrixVente(dto.getPrixVente());
                    full.setPrixLocation(dto.getPrixLocation());
                    full.setStatut(dto.getStatut());
                    full.setSurface(dto.getSurface());
                    full.setNbChambres(dto.getNbChambres());
                    full.setAdresse(dto.getAdresse());
                    full.setCountry(dto.getCountry());
                    full.setCity(dto.getCity());
                    full.setLatitude(dto.getLatitude());
                    full.setLongitude(dto.getLongitude());
                    full.setIsActive(dto.getIsActive());
                    full.setCreatedAt(dto.getCreatedAt());
                    full.setUpdatedAt(dto.getUpdatedAt());
                    full.setOwnerType(dto.getOwnerType());
                    full.setAgencyAdminId(dto.getAgencyAdminId());
                    full.setAgencyAdminName(dto.getAgencyAdminName());
                    full.setHasMainImage(dto.isHasMainImage());
                    full.setMainImageUrl(dto.getMainImageUrl());
                    full.setHasModel3d(dto.isHasModel3d());
                    full.setModel3dUrl(dto.getModel3dUrl());
                    full.setValidationStatus(dto.getValidationStatus());
                    full.setOwnerRole(dto.getOwnerRole());
                    full.setCreatedById(dto.getCreatedById());
                    full.setCreatedByName(dto.getCreatedByName());
                    full.setCommissionLocked(dto.getCommissionLocked());
                    full.setPriceLocked(dto.getPriceLocked());
                    return full;
                })
                .collect(java.util.stream.Collectors.toList()));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PropertyListDTO>> getAllPropertiesList() {
        User currentUser = securityUtils.getCurrentUser();
        return ResponseEntity.ok(propertyService.getAllPropertiesListForUser(currentUser));
    }

    @GetMapping("/sold")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PropertyListDTO>> getSoldProperties() {
        User currentUser = securityUtils.getCurrentUser();
        return ResponseEntity.ok(propertyService.getSoldPropertiesForUser(currentUser));
    }

    /** Returns VENDU + LOUE properties for the Transactions & Ventes page. */
    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PropertyListDTO>> getAllTransactions() {
        User currentUser = securityUtils.getCurrentUser();
        List<PropertyListDTO> sold   = propertyService.getSoldPropertiesForUser(currentUser);
        List<PropertyListDTO> rented = propertyService.getRentedPropertiesForUser(currentUser);
        java.util.List<PropertyListDTO> all = new java.util.ArrayList<>();
        all.addAll(sold);
        all.addAll(rented);
        all.sort((a, b) -> {
            if (b.getUpdatedAt() == null) return -1;
            if (a.getUpdatedAt() == null) return 1;
            return b.getUpdatedAt().compareTo(a.getUpdatedAt());
        });
        return ResponseEntity.ok(all);
    }

    /** Returns all properties where a specific user is the recorded buyer/tenant. */
    @GetMapping("/buyer/{userId}")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PropertyListDTO>> getPropertiesForBuyer(@PathVariable Long userId) {
        return ResponseEntity.ok(propertyService.getPropertiesForBuyerUser(userId));
    }

    @GetMapping("/expired-rentals")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PropertyListDTO>> getExpiredRentals() {
        User currentUser = securityUtils.getCurrentUser();
        return ResponseEntity.ok(propertyService.getExpiredRentalsForUser(currentUser));
    }

    @GetMapping("/rented")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PropertyListDTO>> getRentedProperties() {
        User currentUser = securityUtils.getCurrentUser();
        return ResponseEntity.ok(propertyService.getRentedPropertiesForUser(currentUser));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PropertyListDTO>> getActiveProperties() {
        User currentUser = securityUtils.getCurrentUser();
        return ResponseEntity.ok(propertyService.getAllPropertiesListForUser(currentUser));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> getPropertyById(@PathVariable Long id) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            return ResponseEntity.ok(propertyService.getPropertyByIdForUser(id, currentUser));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Accès refusé")) {
                log.warn("Access denied to property {}: {}", id, msg);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", msg));
            }
            if (msg != null && msg.contains("non trouvée")) {
                log.info("Property {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", msg));
            }
            // Unexpected server error — log the full stack trace so it's visible in the backend console
            log.error("Unexpected error loading property {}: {}", id, msg, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Erreur interne lors du chargement du bien: " + msg));
        }
    }

    @GetMapping("/recent-light")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<PropertyListDTO>> getRecentPropertiesLight(
            @RequestParam(defaultValue = "5") int limit) {
        User currentUser = securityUtils.getCurrentUser();
        // Filter recent from the visible set
        List<PropertyListDTO> all = propertyService.getAllPropertiesListForUser(currentUser);
        List<PropertyListDTO> recent = all.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(recent);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> createProperty(@RequestBody @Valid CreatePropertyRequest request) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            PropertyDTO created = propertyService.createPropertyForUser(request, currentUser);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> updateProperty(@PathVariable Long id,
                                            @RequestBody UpdatePropertyRequest request) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            return ResponseEntity.ok(propertyService.updatePropertyForUser(id, request, currentUser));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Accès refusé")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @PutMapping("/{id}/validate")
    @PreAuthorize("hasAnyRole('RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> validateProperty(
            @PathVariable Long id,
            @RequestBody(required = false) ValidatePropertyRequest body) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            Double commissionPct = body != null ? body.getCommissionPercentage() : null;
            return ResponseEntity.ok(
                    propertyService.validateProperty(id, currentUser, commissionPct));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Accès refusé")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> rejectProperty(@PathVariable Long id,
                                            @RequestBody RejectPropertyRequest request) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            return ResponseEntity.ok(propertyService.rejectProperty(id, request.getReason(), currentUser));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Accès refusé")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    // ✅ Méthodes d'approbation de vente - CORRIGÉES avec userRepository injecté
    @PutMapping("/{id}/approve-sale")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PropertyDTO> approvePendingSale(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        log.info("User {} approuve la vente pour la propriété {}", currentUser.getId(), id);
        return ResponseEntity.ok(propertyService.approvePendingSaleForUser(id, currentUser));
    }

    @PutMapping("/{id}/reject-sale")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<PropertyDTO> rejectPendingSale(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User currentUser = getCurrentUser();
        String reason = body.getOrDefault("reason", "");
        log.info("User {} rejette la vente pour la propriété {}: {}", currentUser.getId(), id, reason);
        return ResponseEntity.ok(propertyService.rejectPendingSaleForUser(id, reason, currentUser));
    }

    // ✅ Helper method corrigée - utilise userRepository
    private User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return userRepository.findById(ud.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        }
        throw new RuntimeException("Utilisateur non authentifié");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> deleteProperty(@PathVariable Long id) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            propertyService.deletePropertyForUser(id, currentUser);
            return ResponseEntity.ok("Propriété désactivée avec succès");
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Accès refusé")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> updatePropertyStatus(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateStatusRequest request) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            PropertyDTO updated = propertyService.updatePropertyStatusForUser(
                    id, request.getStatut(), request.getRentalDurationMonths(), currentUser);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Accès refusé")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @PostMapping("/{id}/direct-sale")
    @PreAuthorize("hasAnyRole('COMMERCIAL', 'RESPONSABLE_COMMERCIAL', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> directSale(@PathVariable Long id,
                                        @RequestBody DirectSaleRequest request) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            PropertyListDTO updated = propertyService.processDirectSale(id, request, currentUser);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Accès refusé")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @PutMapping("/{id}/commission")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> updatePropertyCommission(@PathVariable Long id,
                                                      @RequestBody UpdateCommissionRequest request) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            propertyService.getPropertyByIdForUser(id, currentUser); // access check
            return ResponseEntity.ok(propertyService.updatePropertyCommission(id, request, currentUser));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Accès refusé")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @PutMapping("/region/{region}/commission")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> updateCommissionByRegion(
            @PathVariable String region,
            @RequestParam Double commissionPercentage) {
        int updatedCount = propertyService.updateCommissionByRegion(region, commissionPercentage);
        return ResponseEntity.ok(Map.of(
                "message", updatedCount + " propriétés mises à jour",
                "updatedCount", updatedCount,
                "region", region,
                "newCommission", commissionPercentage));
    }

    // ==================== SHARING (SUPER_ADMIN ONLY) ====================

    /**
     * GET /api/properties/{id}/sharing
     * Returns all agency admins with their share-request status for this property.
     * Uses the new workflow — shows PENDING/ACCEPTED/REJECTED instead of a boolean.
     */
    @GetMapping("/{id}/sharing")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<AgencyAdminDTO>> getSharingInfo(@PathVariable Long id) {
        return ResponseEntity.ok(shareRequestService.getAgenciesWithShareStatus(id));
    }

    /**
     * DELETE /api/properties/{id}/sharing/{adminId}
     * Revokes an accepted share (removes PropertySharedAgency) and cancels any pending request.
     */
    @DeleteMapping("/{id}/sharing/{adminId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> revokeSharing(@PathVariable Long id,
                                           @PathVariable Long adminId) {
        try {
            User currentUser = securityUtils.getCurrentUser();
            propertyService.revokePropertySharing(id, adminId, currentUser);
            return ResponseEntity.ok(Map.of("message", "Partage révoqué"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}