package com.immobilier.backend.dto;

import com.immobilier.backend.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationDTO {

    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private boolean read;
    private String relatedEntityType;
    private Long relatedEntityId;
    private LocalDateTime createdAt;
}
