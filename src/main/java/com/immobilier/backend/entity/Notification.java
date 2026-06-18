package com.immobilier.backend.entity;

import com.immobilier.backend.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_recipient",  columnList = "recipient_id"),
    @Index(name = "idx_notif_is_read",    columnList = "is_read"),
    @Index(name = "idx_notif_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    // Links back to the share request that triggered this notification
    @Column(name = "related_entity_type", length = 40)
    private String relatedEntityType; // e.g. "PROPERTY_SHARE_REQUEST"

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
