package com.immobilier.backend.controller;

import com.immobilier.backend.dto.CommercialPerformanceDTO;
import com.immobilier.backend.dto.CommissionDetailDTO;
import com.immobilier.backend.dto.CommissionSummaryDTO;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.security.SecurityUtils;
import com.immobilier.backend.service.CommissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dedicated read/manage surface for COMMERCIAL / RESPONSABLE_COMMERCIAL
 * commissions — identical architecture to {@link AgencyCommissionController},
 * backed by the unified {@link com.immobilier.backend.entity.Commission}
 * entity (beneficiaryType = "STAFF"). No new table, no duplicated math.
 *
 * Scope (enforced server-side in CommissionService):
 *  - SUPER_ADMIN → every agency's staff commissions
 *  - ADMIN       → only the staff of their own agency
 *  - COMMERCIAL / RESPONSABLE_COMMERCIAL → only their own rows
 *
 * A STAFF commission only exists AFTER an ADMIN/SUPER_ADMIN approves the
 * cross-ownership sale validation — never before (see PropertyOwnershipService).
 */
@RestController
@RequestMapping("/api/commercial-commissions")
@RequiredArgsConstructor
public class CommercialCommissionController {

    private static final String TYPE = "STAFF";

    private final CommissionService commissionService;
    private final SecurityUtils securityUtils;

    /** List staff commissions, role-scoped, optionally filtered by PAID/PENDING. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<CommissionDetailDTO>> list(
            @RequestParam(required = false) String status) {
        User me = securityUtils.getCurrentUser();
        return ResponseEntity.ok(commissionService.listDetailed(me, TYPE, status));
    }

    /** Totals (total / paid / pending / count) for the page header + dashboard card. */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<CommissionSummaryDTO> summary() {
        User me = securityUtils.getCurrentUser();
        return ResponseEntity.ok(commissionService.summary(me, TYPE));
    }

    /** Per-commercial performance for the Gestion commerciale page. */
    @GetMapping("/performance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<CommercialPerformanceDTO>> performance() {
        User me = securityUtils.getCurrentUser();
        return ResponseEntity.ok(commissionService.commercialPerformance(me));
    }

    /**
     * Mark a STAFF commission paid — only ADMIN / SUPER_ADMIN may pay
     * (a commercial can never mark their own commission as paid).
     * Returns the updated row (never an empty body).
     */
    @PutMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<CommissionDetailDTO> markPaid(@PathVariable Long id) {
        return ResponseEntity.ok(commissionService.markPaidScoped(id, TYPE));
    }
}
