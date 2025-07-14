package com.chatapp.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private Long id;

    @NotNull(message = "Conversation ID is required")
    private Long conversationId;

    @NotNull(message = "Sender ID is required")
    private Long senderId;

    @Size(max = 5000, message = "Message content cannot exceed 5000 characters")
    private String content;

    @NotNull(message = "Message type is required")
    private String messageType; // TEXT, IMAGE, VIDEO, DOCUMENT, AUDIO

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sentAt;

    private Boolean isDeleted;
    private Boolean isEdited;
    private String status; // SENT, DELIVERED, READ

    // Sender information
    private String senderUsername;
    private String senderDisplayName;
    private String senderProfileImage;

    // Attachments
    private List<MessageAttachmentDTO> attachments;

    // For pagination
    private Long totalMessages;
    private Integer currentPage;
    private Integer totalPages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageAttachmentDTO {
        private Long id;
        private String fileName;
        private String filePath;
        private Long fileSize;
        private String mimeType;
        private LocalDateTime uploadedAt;
    }

    // Factory method for creating text messages
    public static MessageDTO createTextMessage(Long conversationId, Long senderId, String content) {
        return MessageDTO.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .content(content)
                .messageType("TEXT")
                .sentAt(LocalDateTime.now())
                .isDeleted(false)
                .isEdited(false)
                .status("SENT")
                .build();
    }

    // Factory method for creating attachment messages
    public static MessageDTO createAttachmentMessage(Long conversationId, Long senderId, String messageType, List<MessageAttachmentDTO> attachments) {
        return MessageDTO.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .messageType(messageType)
                .attachments(attachments)
                .sentAt(LocalDateTime.now())
                .isDeleted(false)
                .isEdited(false)
                .status("SENT")
                .build();
    }
}
