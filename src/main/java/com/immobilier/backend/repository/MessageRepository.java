package com.immobilier.backend.repository;

import com.immobilier.backend.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByReceiverIdOrderByCreatedAtDesc(Long receiverId);

    long countByReceiverIdAndIsReadFalse(Long receiverId);

    @Query("SELECT m FROM Message m WHERE (m.sender.id = :userId OR m.receiver.id = :userId) ORDER BY m.createdAt DESC")
    List<Message> findAllForUser(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.isRead = true WHERE m.receiver.id = :receiverId AND m.isRead = false")
    int markAllReadForReceiver(Long receiverId);

    @Query("SELECT m FROM Message m WHERE (m.sender.id = :user1 AND m.receiver.id = :user2) OR (m.sender.id = :user2 AND m.receiver.id = :user1) ORDER BY m.createdAt ASC")
    List<Message> findConversationBetween(@org.springframework.data.repository.query.Param("user1") Long user1,
                                          @org.springframework.data.repository.query.Param("user2") Long user2);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.isRead = true WHERE m.sender.id = :senderId AND m.receiver.id = :receiverId AND m.isRead = false")
    int markConversationReadForReceiver(@org.springframework.data.repository.query.Param("senderId") Long senderId,
                                        @org.springframework.data.repository.query.Param("receiverId") Long receiverId);
}
