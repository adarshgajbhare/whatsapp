package com.chatapp.whatsapp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity

@Table(name = "conversation_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "role")
    @Builder.Default
    private String role = "MEMBER"; // ADMIN, MEMBER, MODERATOR

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Column(name = "is_muted")
    @Builder.Default
    private Boolean isMuted = false;

    @Column(name = "muted_until")
    private LocalDateTime mutedUntil;

    @Column(name = "notification_settings")
    @Builder.Default
    private String notificationSettings = "ALL"; // ALL, MENTIONS, NONE

    // Helper methods
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isMember() {
        return "MEMBER".equals(role);
    }

    public boolean isModerator() {
        return "MODERATOR".equals(role);
    }

    public boolean canAdministrate() {
        return isAdmin() || isModerator();
    }

    public void leave() {
        this.isActive = false;
        this.leftAt = LocalDateTime.now();
    }

    public void rejoin() {
        this.isActive = true;
        this.leftAt = null;
        this.joinedAt = LocalDateTime.now();
    }

    public void mute(LocalDateTime until) {
        this.isMuted = true;
        this.mutedUntil = until;
    }

    public void unmute() {
        this.isMuted = false;
        this.mutedUntil = null;
    }

    public boolean isCurrentlyMuted() {
        if (!isMuted) return false;
        if (mutedUntil == null) return true;
        return LocalDateTime.now().isBefore(mutedUntil);
    }

    public void updateLastRead(Long messageId) {
        this.lastReadMessageId = messageId;
        this.lastReadAt = LocalDateTime.now();
    }

    public void setRole(String newRole) {
        this.role = newRole;
    }

    public void setNotificationSettings(String settings) {
        this.notificationSettings = settings;
    }

    public boolean hasReadMessage(Long messageId) {
        return lastReadMessageId != null && lastReadMessageId >= messageId;
    }

    public boolean shouldReceiveNotification(String messageType) {
        if (isCurrentlyMuted()) return false;

        switch (notificationSettings) {
            case "NONE":
                return false;
            case "MENTIONS":
                return "MENTION".equals(messageType);
            case "ALL":
            default:
                return true;
        }
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
