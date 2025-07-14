package com.chatapp.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class ChatMessageDTO {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderUsername;
    private String senderDisplayName;
    private String content;
    private String messageType; // TEXT, IMAGE, VIDEO, DOCUMENT, AUDIO
    private List<AttachmentDTO> attachments;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sentAt;

    private Boolean isDeleted;
    private Boolean isEdited;
    private String status; // SENT, DELIVERED, READ

    // For real-time messaging via WebSocket
    private String action; // SEND_MESSAGE, TYPING, STOP_TYPING, MESSAGE_READ
    private String recipientUsername;

    // File upload related
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String mimeType;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentDTO {
        private Long id;
        private String fileName;
        private String filePath;
        private Long fileSize;
        private String mimeType;
        private LocalDateTime uploadedAt;
    }
}