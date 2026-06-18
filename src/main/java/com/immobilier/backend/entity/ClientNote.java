// com.immobilier.backend.entity.ClientNote.java
package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_notes")
@Data
public class ClientNote {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private User client;
    
    @ManyToOne
    @JoinColumn(name = "commercial_id", nullable = false)
    private User commercial;
    
    @Column(columnDefinition = "TEXT")
    private String note;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}