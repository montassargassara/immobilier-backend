package com.immobilier.backend.service;

import com.immobilier.backend.dto.NotificationDTO;
import com.immobilier.backend.entity.Notification;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.NotificationType;
import com.immobilier.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // ─── Create ──────────────────────────────────────────────────────────────

    @Transactional
    public void create(User recipient, NotificationType type, String title,
                       String message, Long relatedEntityId) {
        create(recipient, type, title, message, deriveEntityType(type), relatedEntityId);
    }

    @Transactional
    public void create(User recipient, NotificationType type, String title,
                       String message, String relatedEntityType, Long relatedEntityId) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setRelatedEntityType(relatedEntityType);
        n.setRelatedEntityId(relatedEntityId);
        notificationRepository.save(n);
        log.debug("Notification created for user {} type {}", recipient.getId(), type);
    }

    private String deriveEntityType(NotificationType type) {
        if (type == null) return null;
        switch (type) {
            case SHARE_REQUEST_RECEIVED:
            case SHARE_REQUEST_ACCEPTED:
            case SHARE_REQUEST_REJECTED:
            case SHARE_REQUEST_CANCELLED:
                return "PROPERTY_SHARE_REQUEST";
            case AFFILIATE_REGISTRATION:
            case AFFILIATE_APPROVED:
            case AFFILIATE_REJECTED:
            case AFFILIATE_SUSPENDED:
                return "AFFILIATE";
            case SALE_OFFER_RECEIVED:
            case SALE_OFFER_ACCEPTED:
            case SALE_OFFER_REJECTED:
            case SALE_OFFER_COMPLETED:
                return "SALE_OFFER";
            case MONTHLY_BONUS_AWARDED:
                return "MONTHLY_BONUS";
            case PROPERTY_INTEREST_RECEIVED:
            case LEAD_REFUSED:
            case LEAD_CONVERTED_SALE:
            case LEAD_CONVERTED_RENTAL:
            case LEAD_AUTO_REFUSED:
                return "PROPERTY_INTEREST";
            case PROPERTY_AVAILABLE_AGAIN:
                return "PROPERTY";
            case PROPERTY_PENDING_VALIDATION:
            case PROPERTY_VALIDATED:
            case PROPERTY_REJECTED:
            case PROPERTY_MODIFIED:
            case COMMISSION_REQUIRED:
            case PROPERTY_SOLD_BY_AGENCY:
                return "PROPERTY";
            default:
                return null;
        }
    }

    // ─── Read ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NotificationDTO> getAll(User user) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countUnread(User user) {
        return notificationRepository.countByRecipientIdAndReadFalse(user.getId());
    }

    // ─── Mark read ───────────────────────────────────────────────────────────

    @Transactional
    public void markRead(Long notificationId, User user) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getRecipient().getId().longValue() != user.getId().longValue()) {
                throw new SecurityException("Accès refusé");
            }
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void markAllRead(User user) {
        notificationRepository.markAllReadForRecipient(user.getId());
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private NotificationDTO toDTO(Notification n) {
        return NotificationDTO.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .read(n.isRead())
                .relatedEntityType(n.getRelatedEntityType())
                .relatedEntityId(n.getRelatedEntityId())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
