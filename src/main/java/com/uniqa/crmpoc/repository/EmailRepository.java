package com.uniqa.crmpoc.repository;

import com.uniqa.crmpoc.domain.Email;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailRepository extends JpaRepository<Email, Long> {
    Optional<Email> findByMessageId(String messageId);
    List<Email> findByArchivedFalseOrderByReceivedAtDesc();
    List<Email> findByFinalCategoryAndArchivedFalseOrderByReceivedAtDesc(String finalCategory);
}
