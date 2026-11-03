package com.codedoctor.repository;

import com.codedoctor.model.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByReviewIdOrderByCreatedAtAscIdAsc(Long reviewId);
    void deleteByReviewId(Long reviewId);
}
