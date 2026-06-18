package com.immobilier.backend.controller;

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
 * Dedicated read/manage surface for AGENCY commissions.
 *
 * Backed entirely by the unified {@link com.immobilier.backend.entity.Commission}
 * entity (beneficiaryType = "AGENCY"). No new table, no duplicated math —
 * commissions are recorded only on real validated completion via
 * {@code CommissionService.recordForCompletedSale}.
 *
 * Visibility: SUPER_ADMIN ONLY. Agency commissions are the money agencies
 * earn on the Super Admin's shared properties — an agency ADMIN must never
 * see this page, its API, its KPIs or its dashboard card.
 */
@RestController
@RequestMapping("/api/agency-commissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AgencyCommissionController {

    private static final String TYPE = "AGENCY";

    private final CommissionService commissionService;
    private final SecurityUtils securityUtils;

    /** List agency commissions, role-scoped, optionally filtered by PAID/PENDING. */
    @GetMapping
    public ResponseEntity<List<CommissionDetailDTO>> list(
            @RequestParam(required = false) String status) {
        User me = securityUtils.getCurrentUser();
        return ResponseEntity.ok(commissionService.listDetailed(me, TYPE, status));
    }

    /** Totals (total / paid / pending / count) for the page header + dashboard card. */
    @GetMapping("/summary")
    public ResponseEntity<CommissionSummaryDTO> summary() {
        User me = securityUtils.getCurrentUser();
        return ResponseEntity.ok(commissionService.summary(me, TYPE));
    }

    /** Mark an AGENCY commission paid — returns the updated row (never empty body). */
    @PutMapping("/{id}/pay")
    public ResponseEntity<CommissionDetailDTO> markPaid(@PathVariable Long id) {
        return ResponseEntity.ok(commissionService.markPaidScoped(id, TYPE));
    }
}
