package com.immobilier.backend.service;

import com.immobilier.backend.dto.AgencyApplicationDTO;
import com.immobilier.backend.dto.CreateAgencyRequest;
import com.immobilier.backend.entity.AgencyApplication;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.AgencyApplicationStatus;
import com.immobilier.backend.enums.NotificationType;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.AgencyApplicationRepository;
import com.immobilier.backend.repository.UserRepository;
import com.immobilier.backend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyRegistrationService {

    private final UserRepository userRepository;
    private final AgencyApplicationRepository agencyApplicationRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ─── Public registration ─────────────────────────────────────────────────

    @Transactional
    public AgencyApplicationDTO register(CreateAgencyRequest request) {
        log.info("New agency registration request: {} — {}", request.getAgencyName(), request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        String tel = request.getTelephone();
        user.setTelephone((tel != null && !tel.isBlank()) ? tel : null);
        user.setRole(RoleType.ADMIN);
        user.setIsActive(false); // blocked until Super Admin approves
        User savedUser = userRepository.save(user);

        AgencyApplication application = new AgencyApplication();
        application.setUser(savedUser);
        application.setAgencyName(request.getAgencyName());
        application.setDescription(request.getDescription());
        application.setStatus(AgencyApplicationStatus.PENDING);
        AgencyApplication saved = agencyApplicationRepository.save(application);

        // Notify all active Super Admins
        userRepository.findByRoleAndIsActiveTrue(RoleType.SUPER_ADMIN).forEach(sa ->
            notificationService.create(
                sa,
                NotificationType.AGENCY_REGISTRATION,
                "Nouvelle demande d'agence",
                String.format("L'agence « %s » (%s) a soumis une demande d'inscription.",
                    request.getAgencyName(), savedUser.getFullName()),
                savedUser.getId()
            )
        );

        log.info("Agency application created (PENDING), userId={}", savedUser.getId());
        return toDTO(saved);
    }

    // ─── Super Admin queries ─────────────────────────────────────────────────

    public List<AgencyApplicationDTO> getPendingApplications() {
        return agencyApplicationRepository
            .findByStatusOrderByCreatedAtDesc(AgencyApplicationStatus.PENDING)
            .stream().map(this::toDTO).toList();
    }

    public List<AgencyApplicationDTO> getAllApplications() {
        return agencyApplicationRepository.findAll()
            .stream().map(this::toDTO).toList();
    }

    // ─── Super Admin actions ─────────────────────────────────────────────────

    @Transactional
    public AgencyApplicationDTO approve(Long applicationId) {
        AgencyApplication app = findOrThrow(applicationId);
        if (app.getStatus() != AgencyApplicationStatus.PENDING) {
            throw new RuntimeException("Cette candidature n'est plus en attente");
        }

        app.setStatus(AgencyApplicationStatus.APPROVED);
        app.setReviewedAt(LocalDateTime.now());
        agencyApplicationRepository.save(app);

        // Link the new agency admin under the approving Super Admin in the hierarchy
        User approver = securityUtils.getCurrentUser();
        User agencyUser = app.getUser();
        agencyUser.setIsActive(true);
        agencyUser.setParent(approver);
        userRepository.save(agencyUser);

        notificationService.create(
            agencyUser,
            NotificationType.AGENCY_APPROVED,
            "Candidature approuvée",
            String.format("Votre agence « %s » a été approuvée. Vous pouvez maintenant vous connecter.",
                app.getAgencyName()),
            agencyUser.getId()
        );

        log.info("Agency application {} approved, userId={}", applicationId, agencyUser.getId());
        return toDTO(app);
    }

    @Transactional
    public AgencyApplicationDTO reject(Long applicationId, String reason) {
        AgencyApplication app = findOrThrow(applicationId);
        if (app.getStatus() != AgencyApplicationStatus.PENDING) {
            throw new RuntimeException("Cette candidature n'est plus en attente");
        }

        app.setStatus(AgencyApplicationStatus.REJECTED);
        app.setRejectionReason(reason);
        app.setReviewedAt(LocalDateTime.now());
        agencyApplicationRepository.save(app);

        User agencyUser = app.getUser();
        Long userId = agencyUser.getId();
        notificationService.create(
            agencyUser,
            NotificationType.AGENCY_REJECTED,
            "Candidature refusée",
            String.format("Votre demande pour l'agence « %s » a été refusée. Raison: %s",
                app.getAgencyName(),
                reason != null && !reason.isBlank() ? reason : "Non précisée"),
            userId != null ? userId : 0L
        );

        log.info("Agency application {} rejected", applicationId);
        return toDTO(app);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private AgencyApplication findOrThrow(Long id) {
        return agencyApplicationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Candidature introuvable: " + id));
    }

    private AgencyApplicationDTO toDTO(AgencyApplication app) {
        AgencyApplicationDTO dto = new AgencyApplicationDTO();
        dto.setId(app.getId());
        dto.setUserId(app.getUser().getId());
        dto.setEmail(app.getUser().getEmail());
        dto.setNom(app.getUser().getNom());
        dto.setPrenom(app.getUser().getPrenom());
        dto.setTelephone(app.getUser().getTelephone());
        dto.setAgencyName(app.getAgencyName());
        dto.setDescription(app.getDescription());
        dto.setStatus(app.getStatus().name());
        dto.setRejectionReason(app.getRejectionReason());
        dto.setCreatedAt(app.getCreatedAt() != null ? app.getCreatedAt().format(FMT) : null);
        dto.setReviewedAt(app.getReviewedAt() != null ? app.getReviewedAt().format(FMT) : null);
        return dto;
    }
}
