package com.immobilier.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "affiliate_transactions", indexes = {
    @Index(name = "idx_transactions_affiliate", columnList = "affiliate_id"),
    @Index(name = "idx_transactions_property", columnList = "property_id"),
    @Index(name = "idx_transactions_date", columnList = "transaction_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AffiliateTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affiliate_id", nullable = false)
    private User affiliate;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;
    
    @Column(name = "property_price", nullable = false)
    private Double propertyPrice;
    
    @Column(name = "commission_percentage", nullable = false)
    private Double commissionPercentage;
    
    @Column(name = "commission_amount", nullable = false)
    private Double commissionAmount;
    
    @Column(name = "transaction_type")
    private String transactionType; // SALE, RENT
    
    @Column(name = "client_email")
    private String clientEmail;
    
    @Column(name = "is_paid")
    private Boolean isPaid = false;
    
    @Column(name = "payment_date")
    private LocalDateTime paymentDate;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}