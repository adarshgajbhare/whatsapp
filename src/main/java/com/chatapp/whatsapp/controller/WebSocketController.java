package com.chatapp.whatsapp.controller;

import com.chatapp.whatsapp.dto.ChatMessageDTO;
import com.chatapp.whatsapp.dto.MessageDTO;
import com.chatapp.whatsapp.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessageDTO chatMessage) {
        log.info("=== WEBSOCKET CONTROLLER: Message Received ===");
        log.info("Payload: ConversationID={}, SenderID={}, Content='{}'",
                chatMessage.getConversationId(), chatMessage.getSenderId(), chatMessage.getContent());

        try {
            // Validate the incoming message
            if (chatMessage == null) {
                log.error("ERROR: Received null ChatMessageDTO");
                return;
            }

            // Log the full payload for debugging
            log.info("Full ChatMessageDTO: {}", chatMessage);

            // Call the service method to save the message
            log.info("Calling MessageService.saveWebSocketMessage()...");
            MessageDTO savedMessage = messageService.saveWebSocketMessage(chatMessage);
            log.info("SUCCESS: Message saved with ID: {}", savedMessage.getId());

            // Prepare the response message to broadcast
            ChatMessageDTO responseMessage = ChatMessageDTO.builder()
                    .id(savedMessage.getId())
                    .conversationId(savedMessage.getConversationId())
                    .senderId(savedMessage.getSenderId())
                    .senderUsername(savedMessage.getSenderUsername())
                    .senderDisplayName(savedMessage.getSenderDisplayName())
                    .content(savedMessage.getContent())
                    .messageType(savedMessage.getMessageType())
                    .sentAt(savedMessage.getSentAt())
                    .status(savedMessage.getStatus())
                    .isDeleted(savedMessage.getIsDeleted())
                    .isEdited(savedMessage.getIsEdited())
                    .build();

            // Send the message to all subscribers of the conversation topic
            String destination = "/topic/conversation/" + savedMessage.getConversationId();
            log.info("Broadcasting message to destination: {}", destination);
            messagingTemplate.convertAndSend(destination, responseMessage);

            log.info("SUCCESS: Message ID {} broadcasted to conversation {}",
                    savedMessage.getId(), savedMessage.getConversationId());

        } catch (IllegalArgumentException e) {
            log.error("VALIDATION ERROR: {}", e.getMessage());
            sendErrorMessage(chatMessage, "Validation Error: " + e.getMessage());

        } catch (SecurityException e) {
            log.error("SECURITY ERROR: {}", e.getMessage());
            sendErrorMessage(chatMessage, "Security Error: " + e.getMessage());

        } catch (RuntimeException e) {
            log.error("RUNTIME ERROR: {}", e.getMessage(), e);
            sendErrorMessage(chatMessage, "Error: " + e.getMessage());

        } catch (Exception e) {
            log.error("UNEXPECTED ERROR: Failed to process message", e);
            sendErrorMessage(chatMessage, "Unexpected error occurred while processing message");
        }
    }

    /**
     * Send error message back to the sender
     */
    private void sendErrorMessage(ChatMessageDTO originalMessage, String errorMessage) {
        if (originalMessage == null || originalMessage.getConversationId() == null) {
            log.error("Cannot send error message - original message or conversation ID is null");
            return;
        }

        try {
            ChatMessageDTO errorResponse = ChatMessageDTO.builder()
                    .conversationId(originalMessage.getConversationId())
                    .senderId(-1L) // System message
                    .senderUsername("SYSTEM")
                    .senderDisplayName("System")
                    .content("❌ " + errorMessage)
                    .messageType("ERROR")
                    .sentAt(LocalDateTime.now())
                    .status("ERROR")
                    .build();

            String destination = "/topic/conversation/" + originalMessage.getConversationId();
            messagingTemplate.convertAndSend(destination, errorResponse);
            log.info("Error message sent to conversation {}: {}", originalMessage.getConversationId(), errorMessage);

        } catch (Exception e) {
            log.error("Failed to send error message", e);
        }
    }

    @MessageMapping("/chat.joinConversation")
    public void joinConversation(@Payload ChatMessageDTO chatMessage) {
        log.info("User {} joining conversation {}", chatMessage.getSenderId(), chatMessage.getConversationId());
        // Implementation for joining conversation
    }

    @MessageMapping("/chat.typing")
    public void sendTypingIndicator(@Payload ChatMessageDTO chatMessage) {
        log.info("Typing indicator from user {} in conversation {}",
                chatMessage.getSenderId(), chatMessage.getConversationId());

        try {
            // Create typing indicator message
            ChatMessageDTO typingMessage = ChatMessageDTO.builder()
                    .conversationId(chatMessage.getConversationId())
                    .senderId(chatMessage.getSenderId())
                    .senderUsername(chatMessage.getSenderUsername())
                    .action("TYPING")
                    .messageType("TYPING")
                    .sentAt(LocalDateTime.now())
                    .build();

            // Send typing indicator to conversation participants
            String destination = "/topic/conversation/" + chatMessage.getConversationId();
            messagingTemplate.convertAndSend(destination, typingMessage);

        } catch (Exception e) {
            log.error("Failed to send typing indicator", e);
        }
    }
}