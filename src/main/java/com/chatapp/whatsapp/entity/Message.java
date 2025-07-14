package com.chatapp.whatsapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "message_type", nullable = false)
    @Builder.Default
    private String messageType = "TEXT"; // TEXT, IMAGE, VIDEO, DOCUMENT, AUDIO

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    private Boolean isEdited = false;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "SENT"; // SENT, DELIVERED, READ

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    // Bidirectional relationship with attachments
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MessageAttachment> attachments = new ArrayList<>();

    // Helper methods
    public void addAttachment(MessageAttachment attachment) {
        attachments.add(attachment);
        attachment.setMessage(this);
    }

    public void removeAttachment(MessageAttachment attachment) {
        attachments.remove(attachment);
        attachment.setMessage(null);
    }

    // Check message types
    public boolean isTextMessage() {
        return "TEXT".equals(messageType);
    }

    public boolean isImageMessage() {
        return "IMAGE".equals(messageType);
    }

    public boolean isVideoMessage() {
        return "VIDEO".equals(messageType);
    }

    public boolean isDocumentMessage() {
        return "DOCUMENT".equals(messageType);
    }

    public boolean isAudioMessage() {
        return "AUDIO".equals(messageType);
    }

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    // Check message status
    public boolean isSent() {
        return "SENT".equals(status);
    }

    public boolean isDelivered() {
        return "DELIVERED".equals(status);
    }

    public boolean isRead() {
        return "READ".equals(status);
    }

    public boolean isReply() {
        return replyToMessageId != null;
    }

    // Mark message as edited
    public void markAsEdited() {
        this.isEdited = true;
        this.editedAt = LocalDateTime.now();
    }

    // Mark message as deleted
    public void markAsDeleted() {
        this.isDeleted = true;
        this.content = null; // Clear content for privacy
    }

    // Update status
    public void updateStatus(String newStatus) {
        this.status = newStatus;
    }

    // Convenience methods for conversationId
    public Long getConversationId() {
        return conversation != null ? conversation.getId() : null;
    }

    public void setConversationId(Long conversationId) {
        if (this.conversation == null) {
            this.conversation = new Conversation();
        }
        this.conversation.setId(conversationId);
    }
}
