// com.immobilier.backend.dto.ClientNoteDTO.java
package com.immobilier.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ClientNoteDTO {
    private Long id;
    private Long clientId;
    private Long commercialId;
    private String commercialNom;
    private String commercialPrenom;
    private String note;
    private LocalDateTime createdAt;
}