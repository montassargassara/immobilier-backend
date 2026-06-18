// com.immobilier.backend.repository.ClientNoteRepository.java
package com.immobilier.backend.repository;

import com.immobilier.backend.entity.ClientNote;
import com.immobilier.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientNoteRepository extends JpaRepository<ClientNote, Long> {
    
    List<ClientNote> findByClientOrderByCreatedAtDesc(User client);
    
    Page<ClientNote> findByClientId(Long clientId, Pageable pageable);
    
    List<ClientNote> findTop5ByClientIdOrderByCreatedAtDesc(Long clientId);
}