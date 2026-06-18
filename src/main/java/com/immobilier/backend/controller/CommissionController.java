package com.immobilier.backend.controller;

import com.immobilier.backend.dto.CommissionRowDTO;
import com.immobilier.backend.dto.MyPerformanceDTO;
import com.immobilier.backend.entity.Commission;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.security.SecurityUtils;
import com.immobilier.backend.service.CommissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/commissions")
@RequiredArgsConstructor
public class CommissionController {

    private final CommissionService commissionService;
    private final SecurityUtils securityUtils;

    /** Personal performance — any internal staff can see only their own. */
    @GetMapping("/my-performance")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RESPONSABLE_COMMERCIAL','COMMERCIAL')")
    public ResponseEntity<MyPerformanceDTO> myPerformance() {
        User me = securityUtils.getCurrentUser();
        return ResponseEntity.ok(commissionService.getMyPerformance(me));
    }

    /** Unified commission history (affiliate + agency + staff), role-scoped. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<CommissionRowDTO>> list(
            @RequestParam(required = false) String status) {
        User me = securityUtils.getCurrentUser();
        return ResponseEntity.ok(commissionService.listScoped(me, status));
    }

    /** Mark an AGENCY/STAFF commission as paid (affiliate payouts use the affiliate endpoint). */
    @PutMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Commission> markPaid(@PathVariable Long id) {
        return ResponseEntity.ok(commissionService.markPaid(id));
    }
}
