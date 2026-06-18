package com.immobilier.backend.controller;

import com.immobilier.backend.dto.ZonePaymentRequestDTO;
import com.immobilier.backend.repository.ZonePaymentRepository;
import com.immobilier.backend.security.CustomUserDetails;
import com.immobilier.backend.service.ZonePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/zone-payments")
@RequiredArgsConstructor
public class ZonePaymentController {

    private final ZonePaymentService zonePaymentService;
    private final ZonePaymentRepository zonePaymentRepository;

    /** Affiliate submits a zone payment request with proof image. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<ZonePaymentRequestDTO> submitRequest(
            @RequestParam String country,
            @RequestParam String city,
            @RequestParam(defaultValue = "false") boolean isPremium,
            @RequestParam("proof") MultipartFile proof) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(zonePaymentService.submitRequest(currentUserId(), country, city, isPremium, proof));
    }

    /** Affiliate views their own payment requests. */
    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('AFFILIATE')")
    public ResponseEntity<List<ZonePaymentRequestDTO>> getMyRequests() {
        return ResponseEntity.ok(zonePaymentService.getMyRequests(currentUserId()));
    }

    /** Serve proof images — SUPER_ADMIN/ADMIN see all; AFFILIATE see only their own. */
    @GetMapping("/proof/{filename}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AFFILIATE')")
    public ResponseEntity<byte[]> getProofImage(@PathVariable String filename) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAffiliate = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_AFFILIATE"));

        if (isAffiliate) {
            Long userId = currentUserId();
            if (!zonePaymentRepository.existsByAffiliateIdAndProofImagePath(userId, filename)) {
                return ResponseEntity.status(403).build();
            }
        }

        try {
            byte[] data = zonePaymentService.getProofImage(filename);
            String contentType = zonePaymentService.getContentType(filename);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        } catch (Exception e) {
            log.warn("Proof image not found: {}", filename);
            return ResponseEntity.notFound().build();
        }
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud) {
            return ud.getUserId();
        }
        throw new RuntimeException("User not authenticated");
    }
}
