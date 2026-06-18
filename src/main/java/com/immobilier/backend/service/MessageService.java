package com.immobilier.backend.service;

import com.immobilier.backend.dto.AllowedUserDTO;
import com.immobilier.backend.dto.MessageDTO;
import com.immobilier.backend.dto.SendMessageRequest;
import com.immobilier.backend.entity.Message;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.enums.RoleType;
import com.immobilier.backend.repository.MessageRepository;
import com.immobilier.backend.repository.UserRepository;
import com.immobilier.backend.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    // ── Send ─────────────────────────────────────────────────────────────────

    @Transactional
    public MessageDTO sendMessage(SendMessageRequest request) {
        User sender = securityUtils.getCurrentUser();
        User receiver = userRepository.findById(Objects.requireNonNull(request.getReceiverId()))
                .orElseThrow(() -> new RuntimeException("Destinataire introuvable"));

        validateCanSendMessage(sender, receiver);

        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(request.getContent().trim());

        return toDTO(messageRepository.save(message));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MessageDTO> getInbox() {
        User currentUser = securityUtils.getCurrentUser();
        return messageRepository.findByReceiverIdOrderByCreatedAtDesc(currentUser.getId())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        User currentUser = securityUtils.getCurrentUser();
        return messageRepository.countByReceiverIdAndIsReadFalse(currentUser.getId());
    }

    @Transactional
    public void markAsRead(Long messageId) {
        User currentUser = securityUtils.getCurrentUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message introuvable"));
        if (!Objects.equals(message.getReceiver().getId(), currentUser.getId())) {
            throw new RuntimeException("Accès refusé");
        }
        message.setRead(true);
        messageRepository.save(message);
    }

    @Transactional
    public void markAllAsRead() {
        User currentUser = securityUtils.getCurrentUser();
        messageRepository.markAllReadForReceiver(currentUser.getId());
    }

    @Transactional(readOnly = true)
    public List<MessageDTO> getRecentConversations() {
        User currentUser = securityUtils.getCurrentUser();
        List<Message> all = messageRepository.findAllForUser(currentUser.getId());

        Map<Long, Message> latestByPartner = new LinkedHashMap<>();
        for (Message m : all) {
            Long partnerId = m.getSender().getId().equals(currentUser.getId())
                    ? m.getReceiver().getId() : m.getSender().getId();
            latestByPartner.putIfAbsent(partnerId, m);
        }

        return latestByPartner.values().stream()
                .limit(6)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ── Allowed users (contacts) ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AllowedUserDTO> getAllowedUsers() {
        User currentUser = securityUtils.getCurrentUser();
        RoleType role = currentUser.getRole();

        List<User> allowed = new ArrayList<>();

        if (role == RoleType.SUPER_ADMIN) {
            // SUPER_ADMIN can talk to all active ADMIN users
            allowed = userRepository.findByRoleAndIsActiveTrue(RoleType.ADMIN);

        } else if (role == RoleType.ADMIN) {
            // ADMIN can talk to SUPER_ADMIN(s) + all COMMERCIAL/RESPONSABLE_COMMERCIAL under them
            userRepository.findByRoleAndIsActiveTrue(RoleType.SUPER_ADMIN).forEach(allowed::add);
            userRepository.findAllDescendants(currentUser.getId()).stream()
                    .filter(u -> (u.getRole() == RoleType.COMMERCIAL
                            || u.getRole() == RoleType.RESPONSABLE_COMMERCIAL)
                            && Boolean.TRUE.equals(u.getIsActive()))
                    .forEach(allowed::add);

        } else if (role == RoleType.RESPONSABLE_COMMERCIAL || role == RoleType.COMMERCIAL) {
            // Find ADMIN ancestor
            Optional<User> adminOpt = userRepository.findTopAdminAncestor(currentUser.getId());
            adminOpt.ifPresent(allowed::add);

            if (adminOpt.isPresent()) {
                User adminAncestor = adminOpt.get();
                List<User> siblings = userRepository.findAllDescendants(adminAncestor.getId());

                if (role == RoleType.RESPONSABLE_COMMERCIAL) {
                    // Can talk to ADMIN + COMMERCIAL of same agency
                    siblings.stream()
                            .filter(u -> u.getRole() == RoleType.COMMERCIAL
                                    && !u.getId().equals(currentUser.getId())
                                    && Boolean.TRUE.equals(u.getIsActive()))
                            .forEach(allowed::add);
                } else {
                    // COMMERCIAL: can talk to ADMIN + RESPONSABLE_COMMERCIAL of same agency
                    siblings.stream()
                            .filter(u -> u.getRole() == RoleType.RESPONSABLE_COMMERCIAL
                                    && !u.getId().equals(currentUser.getId())
                                    && Boolean.TRUE.equals(u.getIsActive()))
                            .forEach(allowed::add);
                }
            }
        }

        // Remove duplicates and self
        return allowed.stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .distinct()
                .map(partner -> buildAllowedUserDTO(currentUser, partner))
                .sorted(Comparator.comparingLong(AllowedUserDTO::getUnreadCount).reversed()
                        .thenComparing(dto -> dto.getLastMessageTime() == null ? "" : dto.getLastMessageTime(),
                                Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    private AllowedUserDTO buildAllowedUserDTO(User currentUser, User partner) {
        List<Message> thread = messageRepository.findConversationBetween(currentUser.getId(), partner.getId());

        AllowedUserDTO dto = new AllowedUserDTO();
        dto.setId(partner.getId());
        dto.setFullName(partner.getFullName());
        dto.setRole(partner.getRole().name());
        dto.setEmail(partner.getEmail());
        dto.setHasConversation(!thread.isEmpty());

        if (!thread.isEmpty()) {
            Message last = thread.get(thread.size() - 1);
            String content = last.getContent();
            dto.setLastMessage(content.length() > 60 ? content.substring(0, 60) + "…" : content);
            dto.setLastMessageTime(last.getCreatedAt() != null ? last.getCreatedAt().toString() : null);
        }

        long unread = thread.stream()
                .filter(m -> m.getSender().getId().equals(partner.getId()) && !m.isRead())
                .count();
        dto.setUnreadCount(unread);

        return dto;
    }

    // ── Conversation thread ───────────────────────────────────────────────────

    @Transactional
    public List<MessageDTO> getConversationWith(Long partnerId) {
        User currentUser = securityUtils.getCurrentUser();
        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        // Security: verify the two users are in the same allowed relationship
        validateCanReadConversation(currentUser, partner);

        // Auto-mark incoming messages in this thread as read
        messageRepository.markConversationReadForReceiver(partnerId, currentUser.getId());

        return messageRepository.findConversationBetween(currentUser.getId(), partnerId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ── Role enforcement ──────────────────────────────────────────────────────

    private void validateCanSendMessage(User sender, User receiver) {
        RoleType senderRole = sender.getRole();
        RoleType receiverRole = receiver.getRole();

        if (sender.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("Vous ne pouvez pas vous envoyer un message à vous-même.");
        }

        if (senderRole == RoleType.CLIENT_PUBLIC || senderRole == RoleType.AFFILIATE) {
            throw new IllegalArgumentException("La messagerie interne n'est pas disponible pour ce rôle.");
        }

        if (receiverRole == RoleType.CLIENT_PUBLIC || receiverRole == RoleType.AFFILIATE) {
            throw new IllegalArgumentException("Ce destinataire ne fait pas partie du système de messagerie interne.");
        }

        // COMMERCIAL and RESPONSABLE_COMMERCIAL cannot message SUPER_ADMIN directly
        if ((senderRole == RoleType.COMMERCIAL || senderRole == RoleType.RESPONSABLE_COMMERCIAL)
                && receiverRole == RoleType.SUPER_ADMIN) {
            throw new IllegalArgumentException(
                    "Les commerciaux ne peuvent pas contacter le Super Admin directement. " +
                    "Passez par votre administrateur d'agence.");
        }

        // Cross-agency check: if neither is SUPER_ADMIN, both must share the same ADMIN ancestor
        if (senderRole != RoleType.SUPER_ADMIN && receiverRole != RoleType.SUPER_ADMIN) {
            Long senderAdminId = resolveAdminAncestorId(sender);
            Long receiverAdminId = resolveAdminAncestorId(receiver);

            if (senderAdminId != null && receiverAdminId != null
                    && !senderAdminId.equals(receiverAdminId)) {
                throw new IllegalArgumentException(
                        "Communication interdite entre agences différentes.");
            }
        }
    }

    private void validateCanReadConversation(User current, User partner) {
        // Use the same logic but catch the exception and rethrow as access denied
        try {
            // Dummy direction check — reading is allowed in both directions
            validateCanSendMessage(current, partner);
        } catch (IllegalArgumentException e) {
            // Allow reading even if you can't send (shows existing history)
            // Only block completely unrelated roles
            if (e.getMessage().contains("agences différentes")
                    || e.getMessage().contains("messagerie interne")) {
                throw new RuntimeException("Accès refusé à cette conversation.");
            }
        }
    }

    private Long resolveAdminAncestorId(User user) {
        if (user.getRole() == RoleType.ADMIN) {
            return user.getId();
        }
        return userRepository.findTopAdminAncestor(user.getId())
                .map(User::getId)
                .orElse(null);
    }

    // ── DTO mapper ────────────────────────────────────────────────────────────

    private MessageDTO toDTO(Message m) {
        MessageDTO dto = new MessageDTO();
        dto.setId(m.getId());
        dto.setSenderId(m.getSender().getId());
        dto.setSenderName(m.getSender().getFullName());
        dto.setSenderRole(m.getSender().getRole().name());
        dto.setReceiverId(m.getReceiver().getId());
        dto.setReceiverName(m.getReceiver().getFullName());
        dto.setReceiverRole(m.getReceiver().getRole().name());
        dto.setContent(m.getContent());
        dto.setRead(m.isRead());
        dto.setCreatedAt(m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
        return dto;
    }
}
