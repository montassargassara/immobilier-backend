package com.immobilier.backend.controller;

import com.immobilier.backend.dto.ApproveValidationRequest;
import com.immobilier.backend.dto.SaleValidationRequestDTO;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.security.SecurityUtils;
import com.immobilier.backend.service.SaleValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sale-validations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RESPONSABLE_COMMERCIAL','COMMERCIAL')")
public class SaleValidationController {

    private final SaleValidationService saleValidationService;
    private final SecurityUtils         securityUtils;

    /** Property owner: see all pending validation requests for their properties. */
    @GetMapping("/pending-for-me")
    public ResponseEntity<List<SaleValidationRequestDTO>> getPendingForMe() {
        User me = securityUtils.getCurrentUser();
        return ResponseEntity.ok(saleValidationService.getPendingForOwner(me));
    }

    /** Requester: see their own requests (all statuses). */
    @GetMapping("/my-requests")
    public ResponseEntity<List<SaleValidationRequestDTO>> getMyRequests() {
        User me = securityUtils.getCurrentUser();
        return ResponseEntity.ok(saleValidationService.getMyRequests(me));
    }

    /** Sidebar badge count — pending requests for the caller's properties. */
    @GetMapping("/pending-count")
    public ResponseEntity<Map<String, Long>> getPendingCount() {
        User me = securityUtils.getCurrentUser();
        return ResponseEntity.ok(Map.of("count", saleValidationService.countPendingForOwner(me)));
    }

    /**
     * Property owner approves a pending validation.
     * The reviewer MUST provide the final price and the commission % —
     * there is no automatic default. Missing/invalid values → 400.
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @RequestBody(required = false) ApproveValidationRequest body) {
        try {
            User me = securityUtils.getCurrentUser();
            return ResponseEntity.ok(saleValidationService.approve(id, me, body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Property owner rejects a pending validation. */
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id,
                                    @RequestBody(required = false) Map<String, String> body) {
        try {
            User me     = securityUtils.getCurrentUser();
            String reason = body != null ? body.get("reason") : null;
            return ResponseEntity.ok(saleValidationService.reject(id, me, reason));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
