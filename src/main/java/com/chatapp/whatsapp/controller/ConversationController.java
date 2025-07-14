package com.chatapp.whatsapp.controller;

import com.chatapp.whatsapp.dto.CreateGroupRequest;
import com.chatapp.whatsapp.dto.MessageDTO;
import com.chatapp.whatsapp.entity.Conversation;
import com.chatapp.whatsapp.entity.ConversationParticipant;
import com.chatapp.whatsapp.entity.Message;
import com.chatapp.whatsapp.entity.User;
import com.chatapp.whatsapp.respository.ConversationParticipantRepository;
import com.chatapp.whatsapp.respository.ConversationRepository;
import com.chatapp.whatsapp.respository.MessageRepository;
import com.chatapp.whatsapp.respository.UserRepository;
import com.chatapp.whatsapp.service.GroupService;
import com.chatapp.whatsapp.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
@Slf4j // Add this annotation for logging
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final GroupService groupService;

    /**
     * NEW - Diagnostic Test Endpoint
     * In Postman: GET http://localhost:8080/api/conversations/test/9
     */
    @GetMapping("/test/{id}")
    public ResponseEntity<String> testIdEndpoint(@PathVariable Long id) {
        log.info("[DIAGNOSTIC] === /test/{id} endpoint was HIT successfully with ID: {} ===", id);
        return ResponseEntity.ok("Success! Received ID: " + id);
    }

    /**
     * Get all conversations for a user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserConversations(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // ... (This method remains unchanged) ...
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
            Page<Conversation> conversationPage = conversationRepository.findUserConversations(userId, pageable);

            List<Map<String, Object>> conversationList = new ArrayList<>();

            for (Conversation conversation : conversationPage.getContent()) {
                Map<String, Object> conversationData = new HashMap<>();
                conversationData.put("id", conversation.getId());
                conversationData.put("name", conversation.getName());
                conversationData.put("conversationType", conversation.getConversationType());
                conversationData.put("createdAt", conversation.getCreatedAt());
                conversationData.put("updatedAt", conversation.getUpdatedAt());
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
                Optional<Message> lastMessage = messageRepository.findLatestMessageInConversation(conversation.getId());
                if (lastMessage.isPresent()) {
                    Map<String, Object> messageData = new HashMap<>();
                    messageData.put("content", lastMessage.get().getContent());
                    messageData.put("senderUsername", getUsernameById(lastMessage.get().getSenderId()));
                    messageData.put("sentAt", lastMessage.get().getSentAt());
                    messageData.put("messageType", lastMessage.get().getMessageType());
                    conversationData.put("lastMessage", messageData);
                }
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
     * Get messages in a conversation - WITH ENHANCED LOGGING
     */
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<Object> getConversationMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sentAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("[DIAGNOSTIC] === Trying to enter getConversationMessages for conversationId: {} ===", conversationId);

        try {
            Sort sort = sortDir.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            log.info("[DIAGNOSTIC] Created Pageable object: {}", pageable);

            Page<MessageDTO> messages = messageService.getConversationMessages(conversationId, pageable);
            log.info("[DIAGNOSTIC] Successfully fetched {} messages for conversationId: {}.", messages.getNumberOfElements(), conversationId);

            return ResponseEntity.ok(messages);

        } catch (Exception e) {
            log.error("[DIAGNOSTIC] === CRITICAL ERROR in getConversationMessages for conversationId {} ===", conversationId, e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "An internal error occurred while fetching messages.");
            errorResponse.put("exception_type", e.getClass().getName());
            errorResponse.put("exception_message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }


    // ... a
    // All other methods like getConversation, createGroup, etc., remain here unchanged.
    // ...
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
    @PostMapping("/group")
    public ResponseEntity<?> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        try {
            Conversation group = groupService.createGroup(request);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Group created successfully");
            response.put("conversationId", group.getId());
            response.put("groupName", group.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @PostMapping("/group/{conversationId}/participants")
    public ResponseEntity<?> addGroupParticipant(
            @PathVariable Long conversationId,
            @RequestParam Long userIdToAdd,
            @RequestParam Long requestingUserId) {
        try {
            groupService.addParticipant(conversationId, userIdToAdd, requestingUserId);
            return ResponseEntity.ok(Map.of("message", "Participant added successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @DeleteMapping("/group/{conversationId}/participants")
    public ResponseEntity<?> removeGroupParticipant(
            @PathVariable Long conversationId,
            @RequestParam Long userIdToRemove,
            @RequestParam Long requestingUserId) {
        try {
            groupService.removeParticipant(conversationId, userIdToRemove, requestingUserId);
            return ResponseEntity.ok(Map.of("message", "Participant removed successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @PutMapping("/{conversationId}/read")
    public ResponseEntity<Map<String, Object>> markConversationAsRead(
            @PathVariable Long conversationId,
            @RequestParam Long userId) {

        try {
            Optional<Message> latestMessage = messageRepository.findLatestMessageInConversation(conversationId);
            if (latestMessage.isPresent()) {
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
    private String getUsernameById(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        return user.map(User::getUsername).orElse("Unknown");
    }

    @GetMapping("/user/{userId}/search-groups")
    public ResponseEntity<Map<String, Object>> searchUserGroups(
            @PathVariable Long userId,
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
            Page<Conversation> groupPage = conversationRepository.findUserGroupsByNameContainingIgnoreCase(userId, name, pageable);

            List<Map<String, Object>> groupList = new ArrayList<>();
            for (Conversation conversation : groupPage.getContent()) {
                Map<String, Object> groupData = new HashMap<>();
                groupData.put("conversationId", conversation.getId());
                groupData.put("groupName", conversation.getName());
                groupData.put("createdAt", conversation.getCreatedAt());
                groupData.put("lastActivity", conversation.getUpdatedAt());
                groupData.put("memberCount", participantRepository.countActiveParticipants(conversation.getId()));
                groupList.add(groupData);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("groups", groupList);
            response.put("currentPage", groupPage.getNumber());
            response.put("totalPages", groupPage.getTotalPages());
            response.put("totalElements", groupPage.getTotalElements());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}