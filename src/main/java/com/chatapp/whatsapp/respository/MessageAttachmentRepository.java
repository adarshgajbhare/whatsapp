package com.chatapp.whatsapp.respository;

import com.chatapp.whatsapp.entity.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {

    /**
     * Find attachments by message ID
     */
    List<MessageAttachment> findByMessageId(Long messageId);

    /**
     * Find attachment by file path
     */
    Optional<MessageAttachment> findByFilePath(String filePath);

    /**
     * Find attachments by MIME type
     */
    @Query("SELECT ma FROM MessageAttachment ma " +
            "WHERE ma.message.conversation.id = :conversationId " +
            "AND ma.mimeType LIKE :mimeTypePattern " +
            "ORDER BY ma.uploadedAt DESC")
    List<MessageAttachment> findByConversationIdAndMimeType(@Param("conversationId") Long conversationId,
                                                            @Param("mimeTypePattern") String mimeTypePattern);

    /**
     * Find all image attachments in conversation
     */
    @Query("SELECT ma FROM MessageAttachment ma " +
            "WHERE ma.message.conversation.id = :conversationId " +
            "AND ma.mimeType LIKE 'image/%' " +
            "ORDER BY ma.uploadedAt DESC")
    List<MessageAttachment> findImageAttachmentsByConversation(@Param("conversationId") Long conversationId);

    /**
     * Find all video attachments in conversation
     */
    @Query("SELECT ma FROM MessageAttachment ma " +
            "WHERE ma.message.conversation.id = :conversationId " +
            "AND ma.mimeType LIKE 'video/%' " +
            "ORDER BY ma.uploadedAt DESC")
    List<MessageAttachment> findVideoAttachmentsByConversation(@Param("conversationId") Long conversationId);

    /**
     * Find all document attachments in conversation
     */
    @Query("SELECT ma FROM MessageAttachment ma " +
            "WHERE ma.message.conversation.id = :conversationId " +
            "AND ma.mimeType NOT LIKE 'image/%' " +
            "AND ma.mimeType NOT LIKE 'video/%' " +
            "AND ma.mimeType NOT LIKE 'audio/%' " +
            "ORDER BY ma.uploadedAt DESC")
    List<MessageAttachment> findDocumentAttachmentsByConversation(@Param("conversationId") Long conversationId);

    /**
     * Find attachments by file name
     */
    @Query("SELECT ma FROM MessageAttachment ma " +
            "WHERE LOWER(ma.fileName) LIKE LOWER(CONCAT('%', :fileName, '%')) " +
            "ORDER BY ma.uploadedAt DESC")
    List<MessageAttachment> findByFileNameContainingIgnoreCase(@Param("fileName") String fileName);

    /**
     * Find attachments larger than specified size
     */
    @Query("SELECT ma FROM MessageAttachment ma " +
            "WHERE ma.fileSize > :minSize " +
            "ORDER BY ma.fileSize DESC")
    List<MessageAttachment> findByFileSizeGreaterThan(@Param("minSize") Long minSize);

    /**
     * Get total storage used by user
     */
    @Query("SELECT COALESCE(SUM(ma.fileSize), 0) FROM MessageAttachment ma " +
            "WHERE ma.message.senderId = :userId")
    Long getTotalStorageByUser(@Param("userId") Long userId);

    /**
     * Get storage used in conversation
     */
    @Query("SELECT COALESCE(SUM(ma.fileSize), 0) FROM MessageAttachment ma " +
            "WHERE ma.message.conversation.id = :conversationId")
    Long getTotalStorageByConversation(@Param("conversationId") Long conversationId);

    /**
     * Delete attachments by message ID
     */
    void deleteByMessageId(Long messageId);

    /**
     * Find recent attachments
     */
    @Query("SELECT ma FROM MessageAttachment ma " +
            "WHERE ma.message.conversation.id = :conversationId " +
            "ORDER BY ma.uploadedAt DESC " +
            "LIMIT :limit")
    List<MessageAttachment> findRecentAttachments(@Param("conversationId") Long conversationId,
                                                  @Param("limit") int limit);
}
