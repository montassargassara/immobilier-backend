package com.immobilier.backend.service;

import com.immobilier.backend.dto.AgencyAdminDTO;
import com.immobilier.backend.dto.CreateShareRequestDTO;
import com.immobilier.backend.dto.PropertyShareRequestDTO;
import com.immobilier.backend.dto.ShareRequestResponseDTO;
import com.immobilier.backend.entity.*;
import com.immobilier.backend.enums.NotificationType;
import com.immobilier.backend.enums.PropertyValidationStatus;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.enums.ShareRequestStatus;
import com.immobilier.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyShareRequestService {

    private final PropertyRepository propertyRepository;
    private final PropertyShareRequestRepository shareRequestRepository;
    private final PropertySharedAgencyRepository sharedAgencyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // ─── Super Admin: create share requests ──────────────────────────────────

    @Transactional
    public List<PropertyShareRequestDTO> createShareRequests(
            Long propertyId, CreateShareRequestDTO dto, User superAdmin) {

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Propriété introuvable: " + propertyId));

        if (!"SUPER_ADMIN_OWNED".equals(property.getOwnerType())) {
            throw new RuntimeException("Seules les propriétés Super Admin peuvent être partagées via ce workflow");
        }
        // A property must be APPROVED before it can be shared.
        if (property.getValidationStatus() != null
                && property.getValidationStatus() != PropertyValidationStatus.APPROVED) {
            throw new RuntimeException("Seules les propriétés approuvées peuvent être partagées avec une agence");
        }

        List<PropertyShareRequestDTO> results = new ArrayList<>();

        for (Long adminId : dto.getAgencyAdminIds()) {
            User agencyAdmin = userRepository.findById(adminId)
                    .orElseThrow(() -> new RuntimeException("Agence introuvable: " + adminId));

            if (agencyAdmin.getRole() != RoleType.ADMIN) {
                throw new RuntimeException("L'utilisateur " + adminId + " n'est pas un ADMIN d'agence");
            }

            // Skip if a pending request already exists
            if (shareRequestRepository.existsByPropertyIdAndAgencyAdminIdAndStatus(
                    propertyId, adminId, ShareRequestStatus.PENDING)) {
                log.warn("Pending share request already exists for property {} agency {}", propertyId, adminId);
                continue;
            }

            PropertyShareRequest request = new PropertyShareRequest();
            request.setProperty(property);
            request.setAgencyAdmin(agencyAdmin);
            request.setSharedBy(superAdmin);
            request.setStatus(ShareRequestStatus.PENDING);
            request.setCommissionPercentage(dto.getCommissionPercentage());
            request.setCommissionType(dto.getCommissionType());
            request.setMessage(dto.getMessage());

            request = shareRequestRepository.save(request);

            // Send notification to agency admin
            String title = "Nouvelle demande de partage de propriété";
            String message = String.format(
                    "Super Admin vous propose le bien \"%s\" (%s). Commission: %s. Ouvrez la demande pour accepter ou refuser.",
                    property.getTitre(),
                    property.getCity() != null ? property.getCity() : "",
                    formatCommission(dto.getCommissionPercentage(), dto.getCommissionType()));

            notificationService.create(agencyAdmin, NotificationType.SHARE_REQUEST_RECEIVED,
                    title, message, request.getId());

            results.add(toDTO(request));
            log.info("Share request {} created for property {} → agency {}", request.getId(), propertyId, adminId);
        }

        return results;
    }

    // ─── Super Admin: cancel a request ───────────────────────────────────────

    @Transactional
    public PropertyShareRequestDTO cancelRequest(Long requestId, User superAdmin) {
        PropertyShareRequest request = getRequestOrThrow(requestId);

        if (request.getSharedBy().getId().longValue() != superAdmin.getId().longValue()) {
            throw new SecurityException("Accès refusé");
        }
        if (request.getStatus() != ShareRequestStatus.PENDING) {
            throw new RuntimeException("Seules les demandes en attente peuvent être annulées");
        }

        request.setStatus(ShareRequestStatus.CANCELLED);
        request.setRespondedAt(LocalDateTime.now());
        request = shareRequestRepository.save(request);

        // Notify agency that request was cancelled
        notificationService.create(
                request.getAgencyAdmin(),
                NotificationType.SHARE_REQUEST_CANCELLED,
                "Demande de partage annulée",
                String.format("La demande de partage pour \"%s\" a été annulée par le Super Admin.",
                        request.getProperty().getTitre()),
                request.getId());

        return toDTO(request);
    }

    // ─── Agency Admin: respond to a request ──────────────────────────────────

    @Transactional
    public PropertyShareRequestDTO respondToRequest(
            Long requestId, ShareRequestResponseDTO dto, User agencyAdmin) {

        PropertyShareRequest request = getRequestOrThrow(requestId);

        if (request.getAgencyAdmin().getId().longValue() != agencyAdmin.getId().longValue()) {
            throw new SecurityException("Accès refusé");
        }
        if (request.getStatus() != ShareRequestStatus.PENDING) {
            throw new RuntimeException("Cette demande a déjà été traitée (statut: " + request.getStatus() + ")");
        }

        if ("ACCEPTED".equals(dto.getResponse())) {
            request.setStatus(ShareRequestStatus.ACCEPTED);
            activateShare(request);

            // Notify super admin of acceptance
            notificationService.create(
                    request.getSharedBy(),
                    NotificationType.SHARE_REQUEST_ACCEPTED,
                    "Demande de partage acceptée",
                    String.format("L'agence \"%s\" a accepté le partage du bien \"%s\".",
                            agencyAdmin.getFullName(), request.getProperty().getTitre()),
                    request.getId());

        } else {
            request.setStatus(ShareRequestStatus.REJECTED);
            request.setRejectionReason(dto.getRejectionReason());

            // Notify super admin of rejection
            notificationService.create(
                    request.getSharedBy(),
                    NotificationType.SHARE_REQUEST_REJECTED,
                    "Demande de partage refusée",
                    String.format("L'agence \"%s\" a refusé le partage du bien \"%s\". Raison: %s",
                            agencyAdmin.getFullName(),
                            request.getProperty().getTitre(),
                            dto.getRejectionReason() != null ? dto.getRejectionReason() : "Non précisée"),
                    request.getId());
        }

        request.setRespondedAt(LocalDateTime.now());
        return toDTO(shareRequestRepository.save(request));
    }

    // ─── Queries ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PropertyShareRequestDTO> getRequestsForAgency(User agencyAdmin) {
        return shareRequestRepository
                .findByAgencyAdminIdOrderByCreatedAtDesc(agencyAdmin.getId())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PropertyShareRequestDTO> getPendingRequestsForAgency(User agencyAdmin) {
        return shareRequestRepository
                .findPendingForAgency(agencyAdmin.getId())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PropertyShareRequestDTO> getRequestsForProperty(Long propertyId, User superAdmin) {
        return shareRequestRepository
                .findByPropertyIdOrderByCreatedAtDesc(propertyId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PropertyShareRequestDTO> getAllRequestsBySuperAdmin(User superAdmin) {
        return shareRequestRepository
                .findAllBySuperAdmin(superAdmin.getId())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PropertyShareRequestDTO getRequestById(Long requestId, User currentUser) {
        PropertyShareRequest r = getRequestOrThrow(requestId);
        boolean isSuperAdmin = currentUser.getRole() == RoleType.SUPER_ADMIN;
        boolean isRecipient = r.getAgencyAdmin().getId().longValue() == currentUser.getId().longValue();
        boolean isOwner = r.getSharedBy().getId().longValue() == currentUser.getId().longValue();

        if (!isSuperAdmin && !isRecipient && !isOwner) {
            throw new SecurityException("Accès refusé");
        }
        return toDTO(r);
    }

    // ─── Agencies available for sharing (with request status) ────────────────

    @Transactional(readOnly = true)
    public List<AgencyAdminDTO> getAgenciesWithShareStatus(Long propertyId) {
        List<User> admins = userRepository.findByRole(RoleType.ADMIN);
        List<Long> acceptedIds = sharedAgencyRepository.findAgencyAdminIdsByPropertyId(propertyId);
        List<PropertyShareRequest> requests = shareRequestRepository
                .findByPropertyIdOrderByCreatedAtDesc(propertyId);

        return admins.stream().map(admin -> {
            boolean accepted = acceptedIds.contains(admin.getId());
            ShareRequestStatus latestStatus = requests.stream()
                    .filter(r -> r.getAgencyAdmin().getId().longValue() == admin.getId().longValue())
                    .findFirst()
                    .map(PropertyShareRequest::getStatus)
                    .orElse(null);

            return new AgencyAdminDTO(
                    admin.getId(),
                    admin.getFullName(),
                    admin.getEmail(),
                    accepted,
                    latestStatus != null ? latestStatus.name() : null);
        }).collect(Collectors.toList());
    }

    // ─── Internal: activate accepted share ───────────────────────────────────

    private void activateShare(PropertyShareRequest request) {
        Long propertyId = request.getProperty().getId();
        Long adminId = request.getAgencyAdmin().getId();

        if (!sharedAgencyRepository.existsByPropertyIdAndAgencyAdminId(propertyId, adminId)) {
            PropertySharedAgency link = new PropertySharedAgency();
            link.setProperty(request.getProperty());
            link.setAgencyAdmin(request.getAgencyAdmin());
            link.setSharedBy(request.getSharedBy());
            sharedAgencyRepository.save(link);
            log.info("PropertySharedAgency created for property {} → agency {}", propertyId, adminId);
        }
    }

    // ─── DTO mapping ─────────────────────────────────────────────────────────

    private PropertyShareRequestDTO toDTO(PropertyShareRequest r) {
        Property p = r.getProperty();
        Double price = p.getPrixVente() != null && p.getPrixVente() > 0 ? p.getPrixVente() : p.getPrixLocation();
        Double commission = calculateCommission(
                r.getCommissionPercentage(), r.getCommissionType(), price);

        String mainImageUrl = null;
        if (p.getMainImageId() != null) {
            mainImageUrl = "/api/images/public/" + p.getMainImageId();
        }

        return PropertyShareRequestDTO.builder()
                .id(r.getId())
                .status(r.getStatus())
                .propertyId(p.getId())
                .propertyTitle(p.getTitre())
                .propertyType(p.getType())
                .propertyStatut(p.getStatut())
                .propertyPrixVente(p.getPrixVente())
                .propertyPrixLocation(p.getPrixLocation())
                .propertyAdresse(p.getAdresse())
                .propertyCity(p.getCity())
                .propertyCountry(p.getCountry())
                .propertySurface(p.getSurface())
                .propertyNbChambres(p.getNbChambres())
                .propertyMainImageUrl(mainImageUrl)
                .commissionPercentage(r.getCommissionPercentage())
                .commissionType(r.getCommissionType())
                .expectedCommissionAmount(commission)
                .sharedById(r.getSharedBy().getId())
                .sharedByName(r.getSharedBy().getFullName())
                .agencyAdminId(r.getAgencyAdmin().getId())
                .agencyAdminName(r.getAgencyAdmin().getFullName())
                .message(r.getMessage())
                .rejectionReason(r.getRejectionReason())
                .createdAt(r.getCreatedAt())
                .respondedAt(r.getRespondedAt())
                .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private PropertyShareRequest getRequestOrThrow(Long id) {
        return shareRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demande introuvable: " + id));
    }

    private Double calculateCommission(Double rate, String type, Double price) {
        if (rate == null || rate == 0 || price == null) return 0.0;
        if ("PERCENTAGE".equals(type)) return price * rate / 100.0;
        return rate; // FIXED
    }

    private String formatCommission(Double rate, String type) {
        if (rate == null || rate == 0) return "Aucune commission";
        if ("PERCENTAGE".equals(type)) return rate + "%";
        return rate + " TND";
    }
}
