package com.chatapp.whatsapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_type", nullable = false)
    @Builder.Default
    private String conversationType = "PRIVATE"; // PRIVATE, GROUP

    @Column(name = "name")
    private String name;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Bidirectional relationship with participants
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ConversationParticipant> participants = new ArrayList<>();

    // Bidirectional relationship with messages
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    // Helper methods
    public void addParticipant(ConversationParticipant participant) {
        participants.add(participant);
        participant.setConversation(this);
    }

    public void removeParticipant(ConversationParticipant participant) {
        participants.remove(participant);
        participant.setConversation(null);
    }

    public void addMessage(Message message) {
        messages.add(message);
        message.setConversation(this);
    }

    public void removeMessage(Message message) {
        messages.remove(message);
        message.setConversation(null);
    }

    // Check if conversation is private
    public boolean isPrivate() {
        return "PRIVATE".equals(conversationType);
    }

    // Check if conversation is group
    public boolean isGroup() {
        return "GROUP".equals(conversationType);
    }

    // Get active participants count
    public long getActiveParticipantsCount() {
        return participants.stream()
                .filter(ConversationParticipant::getIsActive)
                .count();
    }

    // Get latest message
    public Message getLatestMessage() {
        return messages.stream()
                .filter(m -> !m.getIsDeleted())
                .max((m1, m2) -> m1.getSentAt().compareTo(m2.getSentAt()))
                .orElse(null);
    }
}
