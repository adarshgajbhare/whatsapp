package com.chatapp.whatsapp.controller;

import com.chatapp.whatsapp.dto.ChatMessageDTO;
import com.chatapp.whatsapp.dto.MessageDTO;
import com.chatapp.whatsapp.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessageDTO chatMessage) {

        log.info("WebSocketController: Received message payload: {}", chatMessage);

        try {
            // Call our new, dedicated service method
            MessageDTO savedMessage = messageService.saveWebSocketMessage(chatMessage);

            // Prepare the DTO to be sent back to all clients
            ChatMessageDTO responseMessage = ChatMessageDTO.builder()
                    .id(savedMessage.getId())
                    .conversationId(savedMessage.getConversationId())
                    .senderId(savedMessage.getSenderId())
                    .senderUsername(savedMessage.getSenderUsername())
                    .senderDisplayName(savedMessage.getSenderDisplayName())
                    .content(savedMessage.getContent())
                    .messageType(savedMessage.getMessageType())
                    .sentAt(savedMessage.getSentAt())
                    .build();

            // Send the saved message to all subscribers of the conversation topic
            String destination = "/topic/conversation/" + savedMessage.getConversationId();
            messagingTemplate.convertAndSend(destination, responseMessage);

            log.info("WebSocketController: Successfully processed and broadcasted message ID {}", savedMessage.getId());

        } catch (Exception e) {
            // If the service method throws an exception (e.g., conversation not found), it will be logged here.
            log.error("WebSocketController: FAILED to process and save message. Payload: {}", chatMessage, e);
        }
    }

    // The other methods (joinConversation, etc.) can remain unchanged.
    @MessageMapping("/chat.joinConversation")
    public void joinConversation(@Payload ChatMessageDTO chatMessage) {}
    @MessageMapping("/chat.typing")
    public void sendTypingIndicator(@Payload ChatMessageDTO chatMessage) {}
}