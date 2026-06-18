package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class MessageDTO {
    private Long id;
    private Long senderId;
    private String senderName;
    private String senderRole;
    private Long receiverId;
    private String receiverName;
    private String receiverRole;
    private String content;
    private boolean read;
    private String createdAt;
}
