package com.immobilier.backend.repository;

import com.immobilier.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.id = :recipientId AND n.read = false")
    void markAllReadForRecipient(@Param("recipientId") Long recipientId);

    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :recipientId AND n.relatedEntityId = :entityId AND n.relatedEntityType = :entityType")
    List<Notification> findByRecipientAndRelatedEntity(
            @Param("recipientId") Long recipientId,
            @Param("entityId") Long entityId,
            @Param("entityType") String entityType);
}
