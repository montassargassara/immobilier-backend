package com.immobilier.backend.controller;

import com.immobilier.backend.dto.*;
import com.immobilier.backend.service.ClientManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientManagementController {

    private final ClientManagementService clientManagementService;

    // ========== STATISTICS ==========
    
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<Map<String, Object>> getClientStats() {
        return ResponseEntity.ok(clientManagementService.getClientStats());
    }
    
    @GetMapping("/affiliate/{affiliateId}/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL')")
    public ResponseEntity<Map<String, Object>> getAffiliateStats(@PathVariable Long affiliateId) {
        return ResponseEntity.ok(clientManagementService.getAffiliateStats(affiliateId));
    }

    // ========== CREATE ==========
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<ClientDTO> createClient(@Valid @RequestBody CreateClientRequest request) {
        ClientDTO createdClient = clientManagementService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdClient);
    }

    // ========== READ ==========
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<Page<ClientDTO>> getAllClients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        
        return ResponseEntity.ok(clientManagementService.getAllClients(pageable));
    }
    
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<Page<ClientDTO>> searchClients(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(clientManagementService.searchClients(keyword, pageable));
    }
    
    @GetMapping("/by-commercial/{commercialId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<Page<ClientDTO>> getClientsByCommercial(
            @PathVariable Long commercialId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(clientManagementService.getClientsByCommercial(commercialId, pageable));
    }
    
    @GetMapping("/by-budget")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<Page<ClientDTO>> getClientsByBudgetRange(
            @RequestParam(required = true) Double minBudget,
            @RequestParam(required = true) Double maxBudget,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "budgetEstime"));
        return ResponseEntity.ok(clientManagementService.getClientsByBudgetRange(minBudget, maxBudget, pageable));
    }
    
    @GetMapping("/buyers")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<Page<ClientDTO>> getBuyers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "totalAchats"));
        return ResponseEntity.ok(clientManagementService.getBuyers(pageable));
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<ClientDTO> getClientById(@PathVariable Long id) {
        return ResponseEntity.ok(clientManagementService.getClientById(id));
    }

    // ========== UPDATE ==========
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<ClientDTO> updateClient(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClientRequest request) {
        return ResponseEntity.ok(clientManagementService.updateClient(id, request));
    }
    
    @PatchMapping("/{id}/assign-commercial")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL')")
    public ResponseEntity<ClientDTO> assignCommercial(
            @PathVariable Long id,
            @RequestParam Long commercialId) {
        return ResponseEntity.ok(clientManagementService.assignCommercial(id, commercialId));
    }
    
    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<ClientDTO> toggleClientStatus(@PathVariable Long id) {
        return ResponseEntity.ok(clientManagementService.toggleClientStatus(id));
    }

    // ========== DELETE ==========
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteClient(@PathVariable Long id) {
        clientManagementService.deleteClient(id);
        return ResponseEntity.ok(Map.of("message", "Client désactivé avec succès"));
    }

    // ========== NOTES ==========
    
    @PostMapping("/{clientId}/notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<ClientNoteDTO> addClientNote(
            @PathVariable Long clientId,
            @RequestParam Long commercialId,
            @RequestBody Map<String, String> noteRequest) {
        String note = noteRequest.get("note");
        if (note == null || note.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ClientNoteDTO createdNote = clientManagementService.addClientNote(clientId, commercialId, note);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdNote);
    }
    
    @GetMapping("/{clientId}/notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RESPONSABLE_COMMERCIAL', 'COMMERCIAL')")
    public ResponseEntity<List<ClientNoteDTO>> getClientNotes(@PathVariable Long clientId) {
        return ResponseEntity.ok(clientManagementService.getClientNotes(clientId));
    }

    // ========== SHARING MANAGEMENT (PRIVATE CLIENTS) ==========

    @PostMapping("/{clientId}/share/{adminId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> sharePrivateClientWithAgency(
            @PathVariable Long clientId,
            @PathVariable Long adminId) {
        clientManagementService.sharePrivateClientWithAgency(clientId, adminId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{clientId}/share/{adminId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> revokePrivateClientSharing(
            @PathVariable Long clientId,
            @PathVariable Long adminId) {
        clientManagementService.revokePrivateClientSharing(clientId, adminId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{clientId}/available-agencies")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<UserDTO>> getAvailableAgenciesForSharing(@PathVariable Long clientId) {
        return ResponseEntity.ok(clientManagementService.getAvailableAgenciesForSharing(clientId));
    }

}