package com.immobilier.backend.controller;

import com.immobilier.backend.dto.InterestRequestCreateRequest;
import com.immobilier.backend.dto.InterestRequestDTO;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.security.SecurityUtils;
import com.immobilier.backend.service.InterestRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/client/interests")
@RequiredArgsConstructor
public class InterestRequestController {

    private final InterestRequestService interestRequestService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasRole('CLIENT_PUBLIC')")
    public ResponseEntity<?> submit(@Valid @RequestBody InterestRequestCreateRequest request) {
        try {
            User user = securityUtils.getCurrentUser();
            InterestRequestDTO dto = interestRequestService.submit(user, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error submitting interest", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erreur lors de l'envoi de votre intérêt"));
        }
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT_PUBLIC')")
    public ResponseEntity<List<InterestRequestDTO>> mine() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(interestRequestService.myInterests(user));
    }

}
