package com.immobilier.backend.controller;

import com.immobilier.backend.dto.CreateSaleOfferRequest;
import com.immobilier.backend.dto.RespondSaleOfferRequest;
import com.immobilier.backend.dto.SaleOfferDTO;
import com.immobilier.backend.security.CustomUserDetails;
import com.immobilier.backend.service.SaleOfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/sale-offers")
@RequiredArgsConstructor
public class SaleOfferController {

    private final SaleOfferService saleOfferService;

    // ── Affiliate ─────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<SaleOfferDTO> submitOffer(@Valid @RequestBody CreateSaleOfferRequest request) {
        log.info("Affiliate {} submitting sale offer on property {}", currentUserId(), request.getPropertyId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(saleOfferService.submitOffer(currentUserId(), request));
    }

    @GetMapping("/my-offers")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<List<SaleOfferDTO>> getMyOffers() {
        return ResponseEntity.ok(saleOfferService.getMyOffers(currentUserId()));
    }

    @DeleteMapping("/{id}/cancel")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<SaleOfferDTO> cancelOffer(@PathVariable Long id) {
        log.info("Affiliate {} cancelling offer {}", currentUserId(), id);
        return ResponseEntity.ok(saleOfferService.cancelOffer(id, currentUserId()));
    }

    // ── Agency (incoming offers on agency-owned properties) ───────────────────

    @GetMapping("/incoming")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<List<SaleOfferDTO>> getIncomingOffers() {
        return ResponseEntity.ok(saleOfferService.getIncomingOffersForAgency(currentUserId()));
    }

    @GetMapping("/incoming/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<List<SaleOfferDTO>> getIncomingPendingOffers() {
        return ResponseEntity.ok(saleOfferService.getIncomingPendingForAgency(currentUserId()));
    }

    @PutMapping("/{id}/respond")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESPONSABLE_COMMERCIAL', 'SUPER_ADMIN')")
    public ResponseEntity<SaleOfferDTO> respondToOffer(
            @PathVariable Long id,
            @Valid @RequestBody RespondSaleOfferRequest request) {
        log.info("User {} responding to offer {}: {}", currentUserId(), id, request.getResponse());
        return ResponseEntity.ok(saleOfferService.respondToOffer(id, request, currentUserId()));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<SaleOfferDTO> completeOffer(@PathVariable Long id) {
        log.info("User {} completing offer {}", currentUserId(), id);
        return ResponseEntity.ok(saleOfferService.completeOffer(id, currentUserId()));
    }

    // ── Super Admin: full visibility ──────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<SaleOfferDTO>> getAllOffers() {
        return ResponseEntity.ok(saleOfferService.getAllOffers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AFFILIATE')")
    public ResponseEntity<SaleOfferDTO> getOfferById(@PathVariable Long id) {
        return ResponseEntity.ok(saleOfferService.getOfferById(id));
    }

    // ── Security context helper ───────────────────────────────────────────────

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getUserId();
        }
        throw new RuntimeException("User not authenticated");
    }
}
