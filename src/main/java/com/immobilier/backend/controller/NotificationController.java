package com.immobilier.backend.controller;

import com.immobilier.backend.dto.NotificationDTO;
import com.immobilier.backend.entity.User;
import com.immobilier.backend.security.SecurityUtils;
import com.immobilier.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationDTO>> getAll() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(notificationService.getAll(user));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        User user = securityUtils.getCurrentUser();
        long count = notificationService.countUnread(user);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        User user = securityUtils.getCurrentUser();
        notificationService.markRead(id, user);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllRead() {
        User user = securityUtils.getCurrentUser();
        notificationService.markAllRead(user);
        return ResponseEntity.noContent().build();
    }
}
