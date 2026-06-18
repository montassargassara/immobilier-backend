package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_shared_agencies", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"client_id", "admin_id"})
})
@Data
public class ClientSharedAgency {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientInfo client;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin; // ADMIN user
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_by_id", nullable = false)
    private User sharedBy; // SUPER_ADMIN who shared
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}