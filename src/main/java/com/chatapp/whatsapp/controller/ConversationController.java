package com.chatapp.whatsapp.controller;

import com.chatapp.whatsapp.dto.MessageDTO;
import com.chatapp.whatsapp.entity.Conversation;
import com.chatapp.whatsapp.entity.ConversationParticipant;
import com.chatapp.whatsapp.entity.Message;
import com.chatapp.whatsapp.entity.User;
import com.chatapp.whatsapp.respository.ConversationParticipantRepository;
import com.chatapp.whatsapp.respository.ConversationRepository;
import com.chatapp.whatsapp.respository.MessageRepository;
import com.chatapp.whatsapp.respository.UserRepository;
import com.chatapp.whatsapp.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;

    /**
     * Get all conversations for a user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserConversations(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
            Page<Conversation> conversationPage = conversationRepository.findUserConversations(userId, pageable);

            List<Map<String, Object>> conversationList = new ArrayList<>();

            for (Conversation conversation : conversationPage.getContent()) {
                Map<String, Object> conversationData = new HashMap<>();
                conversationData.put("id", conversation.getId());
                conversationData.put("conversationType", conversation.getConversationType());
                conversationData.put("createdAt", conversation.getCreatedAt());
                conversationData.put("updatedAt", conversation.getUpdatedAt());

                // Get participants
                List<ConversationParticipant> participants = participantRepository.findByConversationIdAndIsActiveTrue(conversation.getId());
                List<Map<String, Object>> participantList = new ArrayList<>();

                for (ConversationParticipant participant : participants) {
                    Optional<User> user = userRepository.findById(participant.getUserId());
                    if (user.isPresent()) {
                        Map<String, Object> participantData = new HashMap<>();
                        participantData.put("id", user.get().getId());
                        participantData.put("username", user.get().getUsername());
                        participantData.put("displayName", user.get().getDisplayName());
                        participantData.put("isOnline", user.get().getIsOnline());
                        participantList.add(participantData);
                    }
                }
                conversationData.put("participants", participantList);

                // Get last message
                Optional<Message> lastMessage = messageRepository.findLatestMessageInConversation(conversation.getId());
                if (lastMessage.isPresent()) {
                    Map<String, Object> messageData = new HashMap<>();
                    messageData.put("content", lastMessage.get().getContent());
                    messageData.put("senderUsername", getUsernameById(lastMessage.get().getSenderId()));
                    messageData.put("sentAt", lastMessage.get().getSentAt());
                    messageData.put("messageType", lastMessage.get().getMessageType());
                    conversationData.put("lastMessage", messageData);
                }

                // Get unread count
                Long unreadCount = messageRepository.countUnreadMessages(conversation.getId(), userId);
                conversationData.put("unreadCount", unreadCount);

                conversationList.add(conversationData);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("conversations", conversationList);
            response.put("currentPage", conversationPage.getNumber());
            response.put("totalPages", conversationPage.getTotalPages());
            response.put("totalElements", conversationPage.getTotalElements());
            response.put("hasNext", conversationPage.hasNext());
            response.put("hasPrevious", conversationPage.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get conversation details
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversation(@PathVariable Long conversationId) {
        try {
            Optional<Conversation> conversation = conversationRepository.findById(conversationId);
            if (conversation.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> conversationData = new HashMap<>();
            conversationData.put("id", conversation.get().getId());
            conversationData.put("conversationType", conversation.get().getConversationType());
            conversationData.put("name", conversation.get().getName());
            conversationData.put("createdAt", conversation.get().getCreatedAt());
            conversationData.put("updatedAt", conversation.get().getUpdatedAt());

            // Get participants
            List<ConversationParticipant> participants = participantRepository.findByConversationIdAndIsActiveTrue(conversationId);
            List<Map<String, Object>> participantList = new ArrayList<>();

            for (ConversationParticipant participant : participants) {
                Optional<User> user = userRepository.findById(participant.getUserId());
                if (user.isPresent()) {
                    Map<String, Object> participantData = new HashMap<>();
                    participantData.put("id", user.get().getId());
                    participantData.put("username", user.get().getUsername());
                    participantData.put("displayName", user.get().getDisplayName());
                    participantData.put("isOnline", user.get().getIsOnline());
                    participantData.put("joinedAt", participant.getJoinedAt());
                    participantList.add(participantData);
                }
            }
            conversationData.put("participants", participantList);

            return ResponseEntity.ok(conversationData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get messages in a conversation
     */
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<Page<MessageDTO>> getConversationMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sentAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);
            Page<MessageDTO> messages = messageService.getConversationMessages(conversationId, pageable);

            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create new conversation (optional - for group chats)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createConversation(
            @RequestParam Long creatorId,
            @RequestParam String conversationType,
            @RequestParam(required = false) String name) {

        try {
            Conversation conversation = new Conversation();
            conversation.setConversationType(conversationType);
            conversation.setName(name);
            conversation.setCreatedBy(creatorId);

            Conversation savedConversation = conversationRepository.save(conversation);

            // Add creator as participant
            ConversationParticipant participant = new ConversationParticipant();
            participant.setConversation(savedConversation);
            participant.setUserId(creatorId);
            participant.setIsActive(true);
            participantRepository.save(participant);

            Map<String, Object> response = new HashMap<>();
            response.put("id", savedConversation.getId());
            response.put("conversationType", savedConversation.getConversationType());
            response.put("name", savedConversation.getName());
            response.put("createdAt", savedConversation.getCreatedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Mark conversation as read
     */
    @PutMapping("/{conversationId}/read")
    public ResponseEntity<Map<String, Object>> markConversationAsRead(
            @PathVariable Long conversationId,
            @RequestParam Long userId) {

        try {
            // Find the latest message in conversation
            Optional<Message> latestMessage = messageRepository.findLatestMessageInConversation(conversationId);

            if (latestMessage.isPresent()) {
                // Update participant's last read message
                Optional<ConversationParticipant> participant = participantRepository.findByConversationIdAndUserId(conversationId, userId);
                if (participant.isPresent()) {
                    participant.get().updateLastRead(latestMessage.get().getId());
                    participantRepository.save(participant.get());
                }
            }

            return ResponseEntity.ok(Map.of("message", "Conversation marked as read"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Helper method to get username by ID
     */
    private String getUsernameById(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        return user.map(User::getUsername).orElse("Unknown");
    }
}