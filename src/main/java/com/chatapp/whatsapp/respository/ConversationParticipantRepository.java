package com.chatapp.whatsapp.respository;

import com.chatapp.whatsapp.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    /**
     * Find participants by conversation ID
     */
    @Query("SELECT cp FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId")
    List<ConversationParticipant> findByConversationId(@Param("conversationId") Long conversationId);

    /**
     * Find active participants by conversation ID
     */
    @Query("SELECT cp FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId AND cp.isActive = true")
    List<ConversationParticipant> findByConversationIdAndIsActiveTrue(@Param("conversationId") Long conversationId);

    /**
     * Find conversations by user ID
     */
    List<ConversationParticipant> findByUserId(Long userId);

    /**
     * Find active conversations by user ID
     */
    List<ConversationParticipant> findByUserIdAndIsActiveTrue(Long userId);

    /**
     * Find specific participant
     */
    @Query("SELECT cp FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId AND cp.userId = :userId")
    Optional<ConversationParticipant> findByConversationIdAndUserId(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    /**
     * Check if user is participant in conversation
     */
    @Query("SELECT CASE WHEN COUNT(cp) > 0 THEN true ELSE false END " +
            "FROM ConversationParticipant cp " +
            "WHERE cp.conversation.id = :conversationId " +
            "AND cp.userId = :userId " +
            "AND cp.isActive = true")
    boolean existsByConversationIdAndUserId(@Param("conversationId") Long conversationId,
                                            @Param("userId") Long userId);

    /**
     * Count active participants in conversation
     */
    @Query("SELECT COUNT(cp) FROM ConversationParticipant cp " +
            "WHERE cp.conversation.id = :conversationId " +
            "AND cp.isActive = true")
    Long countActiveParticipants(@Param("conversationId") Long conversationId);

    /**
     * Find conversation participants excluding specific user
     */
    @Query("SELECT cp FROM ConversationParticipant cp " +
            "WHERE cp.conversation.id = :conversationId " +
            "AND cp.userId != :excludeUserId " +
            "AND cp.isActive = true")
    List<ConversationParticipant> findOtherParticipants(@Param("conversationId") Long conversationId,
                                                        @Param("excludeUserId") Long excludeUserId);

    /**
     * Remove participant from conversation
     */
    @Query("UPDATE ConversationParticipant cp SET cp.isActive = false " +
            "WHERE cp.conversation.id = :conversationId " +
            "AND cp.userId = :userId")
    void deactivateParticipant(@Param("conversationId") Long conversationId,
                               @Param("userId") Long userId);

    /**
     * Add participant back to conversation
     */
    @Query("UPDATE ConversationParticipant cp SET cp.isActive = true " +
            "WHERE cp.conversation.id = :conversationId " +
            "AND cp.userId = :userId")
    void reactivateParticipant(@Param("conversationId") Long conversationId,
                               @Param("userId") Long userId);

    /**
     * Find participants who joined after specific time
     */
    @Query("SELECT cp FROM ConversationParticipant cp " +
            "WHERE cp.conversation.id = :conversationId " +
            "AND cp.joinedAt > :afterTime " +
            "AND cp.isActive = true " +
            "ORDER BY cp.joinedAt DESC")
    List<ConversationParticipant> findParticipantsJoinedAfter(@Param("conversationId") Long conversationId,
                                                              @Param("afterTime") java.time.LocalDateTime afterTime);

    /**
     * Get user's most active conversations
     */
    @Query("SELECT cp FROM ConversationParticipant cp " +
            "WHERE cp.userId = :userId " +
            "AND cp.isActive = true " +
            "ORDER BY cp.conversation.updatedAt DESC")
    List<ConversationParticipant> findUsersMostActiveConversations(@Param("userId") Long userId);

    /**
     * Delete participant permanently
     */
    @Query("DELETE FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId AND cp.userId = :userId")
    void deleteParticipant(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
