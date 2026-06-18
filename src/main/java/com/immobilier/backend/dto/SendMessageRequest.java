package com.immobilier.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotNull(message = "Le destinataire est obligatoire")
    private Long receiverId;

    @NotBlank(message = "Le contenu du message est obligatoire")
    private String content;
}
