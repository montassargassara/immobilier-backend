package com.immobilier.backend.entity;

import com.immobilier.backend.enums.AgencyApplicationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agency_applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgencyApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String agencyName;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgencyApplicationStatus status;

    @Column(length = 1000)
    private String rejectionReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime reviewedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        if (status == null) status = AgencyApplicationStatus.PENDING;
    }
}
