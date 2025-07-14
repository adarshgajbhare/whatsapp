package com.chatapp.whatsapp.controller;

import com.chatapp.whatsapp.dto.ChatMessageDTO;
import com.chatapp.whatsapp.dto.MessageDTO;
import com.chatapp.whatsapp.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessageDTO chatMessage,
                            SimpMessageHeaderAccessor headerAccessor,
                            Principal principal) {
        try {
            // Log the incoming message
            log.info("Received message from user {}: {}",
                    chatMessage.getSenderId(), chatMessage.getContent());

            // Save message to database
            MessageDTO savedMessage = messageService.sendMessage(chatMessage);

            // Create response message
            ChatMessageDTO responseMessage = ChatMessageDTO.builder()
                    .id(savedMessage.getId())
                    .conversationId(savedMessage.getConversationId())
                    .senderId(savedMessage.getSenderId())
                    .senderUsername(savedMessage.getSenderUsername())
                    .content(savedMessage.getContent())
                    .messageType(savedMessage.getMessageType())
                    .sentAt(savedMessage.getSentAt())
                    .build();

            // Send message to conversation subscribers
            String destination = "/topic/conversation/" + chatMessage.getConversationId();
            messagingTemplate.convertAndSend(destination, responseMessage);

            log.info("Message sent to conversation {}", chatMessage.getConversationId());

        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);

            // Send error message back to sender
            ChatMessageDTO errorMessage = ChatMessageDTO.builder()
                    .content("Failed to send message: " + e.getMessage())
                    .messageType("ERROR")
                    .sentAt(LocalDateTime.now())
                    .build();

            String errorDestination = "/user/" + chatMessage.getSenderId() + "/queue/errors";
            messagingTemplate.convertAndSend(errorDestination, errorMessage);
        }
    }

    @MessageMapping("/chat.joinConversation")
    public void joinConversation(@Payload ChatMessageDTO chatMessage,
                                 SimpMessageHeaderAccessor headerAccessor) {
        try {
            // Store conversation ID in session attributes
            headerAccessor.getSessionAttributes().put("conversationId", chatMessage.getConversationId());
            headerAccessor.getSessionAttributes().put("userId", chatMessage.getSenderId());

            log.info("User {} joined conversation {}",
                    chatMessage.getSenderId(), chatMessage.getConversationId());

            // Send join notification to conversation
            ChatMessageDTO joinMessage = ChatMessageDTO.builder()
                    .conversationId(chatMessage.getConversationId())
                    .senderId(chatMessage.getSenderId())
                    .senderUsername(chatMessage.getSenderUsername())
                    .content(chatMessage.getSenderUsername() + " joined the conversation")
                    .messageType("JOIN")
                    .sentAt(LocalDateTime.now())
                    .build();

            String destination = "/topic/conversation/" + chatMessage.getConversationId();
            messagingTemplate.convertAndSend(destination, joinMessage);

        } catch (Exception e) {
            log.error("Error joining conversation: {}", e.getMessage(), e);
        }
    }

    @MessageMapping("/chat.typing")
    public void sendTypingIndicator(@Payload ChatMessageDTO chatMessage) {
        try {
            // Create typing indicator message
            ChatMessageDTO typingMessage = ChatMessageDTO.builder()
                    .conversationId(chatMessage.getConversationId())
                    .senderId(chatMessage.getSenderId())
                    .senderUsername(chatMessage.getSenderUsername())
                    .content("is typing...")
                    .messageType("TYPING")
                    .sentAt(LocalDateTime.now())
                    .build();

            // Send typing indicator to conversation (except sender)
            String destination = "/topic/conversation/" + chatMessage.getConversationId();
            messagingTemplate.convertAndSend(destination, typingMessage);

            log.debug("Typing indicator sent for user {} in conversation {}",
                    chatMessage.getSenderId(), chatMessage.getConversationId());

        } catch (Exception e) {
            log.error("Error sending typing indicator: {}", e.getMessage(), e);
        }
    }
}