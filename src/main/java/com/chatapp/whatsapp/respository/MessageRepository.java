package com.chatapp.whatsapp.repository;

import com.chatapp.whatsapp.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * THIS METHOD WORKS - Uses relationship navigation
     */
    Page<Message> findByConversation_IdAndIsDeletedFalse(Long conversationId, Pageable pageable);

    /**
     * REMOVE THIS METHOD - It's causing the error
     */
    // Page<Message> findByConversationIdAndIsDeletedFalse(Long conversationId, Pageable pageable);

    /**
     * Find messages by sender
     */
    Page<Message> findBySenderIdAndIsDeletedFalse(Long senderId, Pageable pageable);

    /**
     * All your @Query methods work fine because they use m.conversation.id
     */
    @Query("SELECT m FROM Message m " +
            "WHERE m.conversation.id = :conversationId " +
            "AND m.messageType = :messageType " +
            "AND m.isDeleted = false " +
            "ORDER BY m.sentAt DESC")
    List<Message> findByConversationIdAndMessageType(@Param("conversationId") Long conversationId,
                                                     @Param("messageType") String messageType);

    @Query("SELECT m FROM Message m " +
            "WHERE m.conversation.id = :conversationId " +
            "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "AND m.isDeleted = false " +
            "ORDER BY m.sentAt DESC")
    List<Message> searchMessagesByContent(@Param("conversationId") Long conversationId,
                                          @Param("searchTerm") String searchTerm);

    @Query("SELECT m FROM Message m " +
            "WHERE m.conversation.id = :conversationId " +
            "AND m.sentAt BETWEEN :startDate AND :endDate " +
            "AND m.isDeleted = false " +
            "ORDER BY m.sentAt DESC")
    List<Message> findMessagesByDateRange(@Param("conversationId") Long conversationId,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    @Query("SELECT m FROM Message m " +
            "WHERE m.conversation.id = :conversationId " +
            "AND m.isDeleted = false " +
            "ORDER BY m.sentAt DESC " +
            "LIMIT 1")
    Optional<Message> findLatestMessageInConversation(@Param("conversationId") Long conversationId);

    @Query("SELECT COUNT(m) FROM Message m " +
            "WHERE m.conversation.id = :conversationId " +
            "AND m.senderId != :userId " +
            "AND m.status != 'read' " +
            "AND m.isDeleted = false")
    Long countUnreadMessages(@Param("conversationId") Long conversationId,
                             @Param("userId") Long userId);

    @Query("SELECT m FROM Message m " +
            "WHERE m.conversation.id = :conversationId " +
            "AND m.messageType IN ('IMAGE', 'VIDEO', 'DOCUMENT', 'AUDIO') " +
            "AND m.isDeleted = false " +
            "ORDER BY m.sentAt DESC")
    List<Message> findMessagesWithAttachments(@Param("conversationId") Long conversationId);

    @Query("SELECT m FROM Message m " +
            "WHERE m.conversation.id = :conversationId " +
            "AND m.sentAt > :afterTime " +
            "AND m.isDeleted = false " +
            "ORDER BY m.sentAt ASC")
    List<Message> findMessagesAfter(@Param("conversationId") Long conversationId,
                                    @Param("afterTime") LocalDateTime afterTime);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.status = :status WHERE m.id = :messageId")
    void updateMessageStatus(@Param("messageId") Long messageId, @Param("status") String status);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.isDeleted = true WHERE m.id = :messageId")
    void markMessageAsDeleted(@Param("messageId") Long messageId);

    @Query("SELECT m FROM Message m " +
            "WHERE m.conversation.id = :conversationId " +
            "AND m.status = :status " +
            "AND m.isDeleted = false " +
            "ORDER BY m.sentAt DESC")
    List<Message> findMessagesByStatus(@Param("conversationId") Long conversationId,
                                       @Param("status") String status);
}
