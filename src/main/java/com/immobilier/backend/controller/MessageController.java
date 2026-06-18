package com.immobilier.backend.controller;

import com.immobilier.backend.dto.AllowedUserDTO;
import com.immobilier.backend.dto.MessageDTO;
import com.immobilier.backend.dto.SendMessageRequest;
import com.immobilier.backend.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/send")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        try {
            return ResponseEntity.ok(messageService.sendMessage(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/inbox")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MessageDTO>> getInbox() {
        return ResponseEntity.ok(messageService.getInbox());
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", messageService.getUnreadCount()));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        try {
            messageService.markAsRead(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markAllAsRead() {
        messageService.markAllAsRead();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MessageDTO>> getRecentConversations() {
        return ResponseEntity.ok(messageService.getRecentConversations());
    }

    /**
     * GET /api/messages/allowed-users
     * Returns the list of users the current user is allowed to message,
     * enriched with last-message preview and unread count.
     */
    @GetMapping("/allowed-users")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AllowedUserDTO>> getAllowedUsers() {
        return ResponseEntity.ok(messageService.getAllowedUsers());
    }

    /**
     * GET /api/messages/conversation/{partnerId}
     * Returns the full message thread between the current user and the partner.
     * Also marks incoming messages from that partner as read.
     */
    @GetMapping("/conversation/{partnerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getConversation(@PathVariable Long partnerId) {
        try {
            return ResponseEntity.ok(messageService.getConversationWith(partnerId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
