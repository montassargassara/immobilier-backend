package com.immobilier.backend.dto;

import lombok.Data;

@Data
public class AllowedUserDTO {
    private Long id;
    private String fullName;
    private String role;
    private String email;
    private String lastMessage;
    private String lastMessageTime;
    private long unreadCount;
    private boolean hasConversation;
}
