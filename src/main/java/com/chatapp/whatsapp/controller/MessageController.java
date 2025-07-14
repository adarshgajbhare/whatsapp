package com.chatapp.whatsapp.controller;

import com.chatapp.whatsapp.dto.ChatMessageDTO;
import com.chatapp.whatsapp.dto.MessageDTO;
import com.chatapp.whatsapp.dto.UserDTO;
import com.chatapp.whatsapp.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MessageController {

    private final MessageService messageService;

    /**
     * Search users by username
     */
    @GetMapping("/users/search")
    public ResponseEntity<List<UserDTO>> searchUsers(
            @RequestParam String username,
            @RequestParam Long currentUserId) {
        List<UserDTO> users = messageService.searchUsers(username, currentUserId);
        return ResponseEntity.ok(users);
    }

    /**
     * Find user by exact username
     */
    @GetMapping("/users/find")
    public ResponseEntity<UserDTO> findUserByUsername(@RequestParam String username) {
        return messageService.findUserByUsername(username)
                .map(user -> ResponseEntity.ok(user))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Send text message
     */
    @PostMapping("/send")
    public ResponseEntity<MessageDTO> sendMessage(
            @RequestParam Long senderId,
            @RequestParam String recipientUsername,
            @RequestParam String content) {
        try {
            MessageDTO message = messageService.sendMessage(senderId, recipientUsername, content);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Send message with file attachment
     */
    @PostMapping("/send/attachment")
    public ResponseEntity<MessageDTO> sendMessageWithAttachment(
            @RequestParam Long senderId,
            @RequestParam String recipientUsername,
            @RequestParam(required = false) String content,
            @RequestParam("file") MultipartFile file) {
        try {
            MessageDTO message = messageService.sendMessageWithAttachment(
                    senderId, recipientUsername, content, file);
            return ResponseEntity.ok(message);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get conversation messages with pagination
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<Page<MessageDTO>> getConversationMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sentAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<MessageDTO> messages = messageService.getConversationMessages(conversationId, pageable);

        return ResponseEntity.ok(messages);
    }

    /**
     * Send typing indicator
     */
    @PostMapping("/typing")
    public ResponseEntity<Void> sendTypingIndicator(
            @RequestParam Long senderId,
            @RequestParam String recipientUsername,
            @RequestParam boolean isTyping) {
        messageService.sendTypingIndicator(senderId, recipientUsername, isTyping);
        return ResponseEntity.ok().build();
    }

    /**
     * WebSocket message handler - Removed to resolve ambiguous mapping with WebSocketController
     * This functionality is now handled by WebSocketController.sendMessage()
     */

    /**
     * WebSocket typing indicator handler - Removed to resolve ambiguous mapping with WebSocketController
     * This functionality is now handled by WebSocketController.sendTypingIndicator()
     */
}
