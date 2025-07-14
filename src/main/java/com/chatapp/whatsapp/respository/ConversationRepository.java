package com.chatapp.whatsapp.respository;

import com.chatapp.whatsapp.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Find private conversation between two users
     */
    @Query("SELECT c FROM Conversation c " +
            "JOIN c.participants p1 " +
            "JOIN c.participants p2 " +
            "WHERE c.conversationType = 'PRIVATE' " +
            "AND p1.userId = :user1Id " +
            "AND p2.userId = :user2Id " +
            "AND p1.isActive = true " +
            "AND p2.isActive = true")
    Optional<Conversation> findPrivateConversationBetweenUsers(@Param("user1Id") Long user1Id,
                                                               @Param("user2Id") Long user2Id);

    /**
     * Find all conversations for a user
     */
    @Query("SELECT DISTINCT c FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE p.userId = :userId " +
            "AND p.isActive = true " +
            "ORDER BY c.updatedAt DESC")
    Page<Conversation> findUserConversations(@Param("userId") Long userId, Pageable pageable);

    /**
     * Find active conversations for a user
     */
    @Query("SELECT c FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE p.userId = :userId " +
            "AND p.isActive = true " +
            "ORDER BY c.updatedAt DESC")
    List<Conversation> findActiveConversationsByUserId(@Param("userId") Long userId);

    /**
     * Find conversation by name (for group chats)
     */
    @Query("SELECT c FROM Conversation c " +
            "WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')) " +
            "AND c.conversationType = 'GROUP' " +
            "ORDER BY c.updatedAt DESC")
    List<Conversation> findByNameContainingIgnoreCase(@Param("name") String name);

    /**
     * Find conversations by type
     */
    List<Conversation> findByConversationType(String conversationType);

    /**
     * Find conversations created by user
     */
    @Query("SELECT c FROM Conversation c " +
            "WHERE c.createdBy = :userId " +
            "ORDER BY c.createdAt DESC")
    List<Conversation> findByCreatedBy(@Param("userId") Long userId);

    /**
     * Count active conversations for user
     */
    @Query("SELECT COUNT(c) FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE p.userId = :userId " +
            "AND p.isActive = true")
    Long countActiveConversationsByUserId(@Param("userId") Long userId);

    /**
     * Find recent conversations with last message
     */
    @Query("SELECT c FROM Conversation c " +
            "JOIN c.participants p " +
            "WHERE p.userId = :userId " +
            "AND p.isActive = true " +
            "AND EXISTS (SELECT 1 FROM Message m WHERE m.conversation = c) " +
            "ORDER BY c.updatedAt DESC")
    List<Conversation> findRecentConversationsWithMessages(@Param("userId") Long userId, Pageable pageable);
}
