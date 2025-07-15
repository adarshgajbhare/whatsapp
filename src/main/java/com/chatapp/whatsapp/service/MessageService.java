package com.chatapp.whatsapp.service;

import com.chatapp.whatsapp.dto.ChatMessageDTO;
import com.chatapp.whatsapp.dto.MessageDTO;
import com.chatapp.whatsapp.dto.UserDTO;
import com.chatapp.whatsapp.entity.Conversation;
import com.chatapp.whatsapp.entity.ConversationParticipant;
import com.chatapp.whatsapp.entity.Message;
import com.chatapp.whatsapp.entity.MessageAttachment;
import com.chatapp.whatsapp.entity.User;
import com.chatapp.whatsapp.respository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageAttachmentRepository attachmentRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * FIXED: Enhanced method for saving messages coming from WebSocket controller
     * This method includes comprehensive validation and error handling
     */
    @Transactional
    public MessageDTO saveWebSocketMessage(ChatMessageDTO chatMessage) {
        log.info("=== WEBSOCKET MESSAGE PROCESSING START ===");
        log.info("Conversation ID: {}, Sender ID: {}, Content: '{}'",
                chatMessage.getConversationId(), chatMessage.getSenderId(), chatMessage.getContent());

        try {
            // 1. Validate input parameters
            if (chatMessage.getConversationId() == null) {
                log.error("ERROR: Conversation ID is null");
                throw new IllegalArgumentException("Conversation ID cannot be null");
            }

            if (chatMessage.getSenderId() == null) {
                log.error("ERROR: Sender ID is null");
                throw new IllegalArgumentException("Sender ID cannot be null");
            }

            if (chatMessage.getContent() == null || chatMessage.getContent().trim().isEmpty()) {
                log.error("ERROR: Message content is null or empty");
                throw new IllegalArgumentException("Message content cannot be null or empty");
            }

            // 2. Find and validate conversation
            log.info("Looking for conversation with ID: {}", chatMessage.getConversationId());
            Conversation conversation = conversationRepository.findById(chatMessage.getConversationId())
                    .orElseThrow(() -> {
                        log.error("CRITICAL: Conversation with ID {} not found", chatMessage.getConversationId());
                        return new RuntimeException("Conversation with ID " + chatMessage.getConversationId() + " not found");
                    });

            log.info("Found conversation: ID={}, Type={}, Name={}",
                    conversation.getId(), conversation.getConversationType(),
                    conversation.getName() != null ? conversation.getName() : "N/A");

            // 3. Validate sender exists
            log.info("Validating sender with ID: {}", chatMessage.getSenderId());
            User sender = userRepository.findById(chatMessage.getSenderId())
                    .orElseThrow(() -> {
                        log.error("CRITICAL: Sender with ID {} not found", chatMessage.getSenderId());
                        return new RuntimeException("Sender with ID " + chatMessage.getSenderId() + " not found");
                    });

            log.info("Found sender: ID={}, Username={}, Active={}",
                    sender.getId(), sender.getUsername(), sender.getIsActive());

            // 4. Check if sender is active
            if (!sender.getIsActive()) {
                log.error("ERROR: Sender {} is not active", chatMessage.getSenderId());
                throw new SecurityException("Sender account is not active");
            }

            // 5. Validate participant membership with detailed logging
            log.info("Checking participant membership for user {} in conversation {}",
                    chatMessage.getSenderId(), conversation.getId());

            // First, get all participants for debugging
            List<ConversationParticipant> allParticipants = participantRepository.findByConversationId(conversation.getId());
            log.info("Total participants in conversation: {}", allParticipants.size());

            for (ConversationParticipant p : allParticipants) {
                log.info("Participant: UserID={}, Active={}, Role={}, JoinedAt={}",
                        p.getUserId(), p.getIsActive(), p.getRole(), p.getJoinedAt());
            }

            // Check if sender is a participant
            ConversationParticipant senderParticipant = participantRepository
                    .findByConversationIdAndUserId(conversation.getId(), chatMessage.getSenderId())
                    .orElseThrow(() -> {
                        log.error("SECURITY_VIOLATION: User {} is not a participant in conversation {}",
                                chatMessage.getSenderId(), conversation.getId());
                        return new SecurityException("Sender is not a participant in this conversation");
                    });

            log.info("Found sender participant: UserID={}, Active={}, Role={}",
                    senderParticipant.getUserId(), senderParticipant.getIsActive(), senderParticipant.getRole());

            // Check if participant is active
            if (!senderParticipant.getIsActive()) {
                log.error("SECURITY_VIOLATION: User {} is not an active participant in conversation {}",
                        chatMessage.getSenderId(), conversation.getId());
                throw new SecurityException("Sender is not an active participant in this conversation");
            }

            // 6. Build the Message entity
            log.info("Building message entity for database insertion");
            Message messageToSave = Message.builder()
                    .conversation(conversation)
                    .senderId(chatMessage.getSenderId())
                    .content(chatMessage.getContent().trim())
                    .messageType(chatMessage.getMessageType() != null ? chatMessage.getMessageType() : "TEXT")
                    .sentAt(LocalDateTime.now())
                    .isDeleted(false)
                    .isEdited(false)
                    .status("SENT")
                    .build();

            log.info("Message entity built: ConversationID={}, SenderID={}, Type={}, Content length={}",
                    messageToSave.getConversationId(), messageToSave.getSenderId(),
                    messageToSave.getMessageType(), messageToSave.getContent().length());

            // 7. Save the message to database
            log.info("Saving message to database...");
            Message savedMessage = messageRepository.save(messageToSave);
            log.info("SUCCESS: Message saved with ID: {}", savedMessage.getId());

            // 8. Update conversation's updatedAt timestamp
            log.info("Updating conversation timestamp...");
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conversation);
            log.info("SUCCESS: Conversation timestamp updated");

            // 9. Convert to DTO and return
            log.info("Converting message to DTO...");
            MessageDTO messageDTO = convertMessageToDTO(savedMessage);
            log.info("SUCCESS: Message DTO created with ID: {}", messageDTO.getId());

            log.info("=== WEBSOCKET MESSAGE PROCESSING COMPLETE ===");
            return messageDTO;

        } catch (Exception e) {
            log.error("=== WEBSOCKET MESSAGE PROCESSING FAILED ===");
            log.error("Error processing message: ConversationID={}, SenderID={}, Error={}",
                    chatMessage.getConversationId(), chatMessage.getSenderId(), e.getMessage(), e);
            throw e; // Re-throw to be handled by the WebSocket controller
        }
    }

    // Enhanced convertMessageToDTO method with null checks
    private MessageDTO convertMessageToDTO(Message message) {
        if (message == null) {
            log.error("ERROR: Attempting to convert null message to DTO");
            throw new IllegalArgumentException("Message cannot be null");
        }

        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversationId());
        dto.setSenderId(message.getSenderId());
        dto.setContent(message.getContent());
        dto.setMessageType(message.getMessageType());
        dto.setSentAt(message.getSentAt());
        dto.setIsDeleted(message.getIsDeleted());
        dto.setStatus(message.getStatus());
        dto.setIsEdited(message.getIsEdited());

        // Safely get sender information
        try {
            userRepository.findById(message.getSenderId()).ifPresent(sender -> {
                dto.setSenderUsername(sender.getUsername());
                dto.setSenderDisplayName(sender.getDisplayName());
                dto.setSenderProfileImage(sender.getProfileImage());
            });
        } catch (Exception e) {
            log.warn("Warning: Could not fetch sender details for message {}: {}", message.getId(), e.getMessage());
        }

        return dto;
    }

    // ... All your other existing methods remain the same ...

    public List<UserDTO> searchUsers(String username, Long currentUserId) {
        List<User> users = userRepository.findByUsernameContainingIgnoreCaseAndIdNotAndIsActiveTrue(username, currentUserId);
        return users.stream().map(this::convertUserToDTO).collect(Collectors.toList());
    }

    public Optional<UserDTO> findUserByUsername(String username) {
        return userRepository.findByUsernameAndIsActiveTrue(username).map(this::convertUserToDTO);
    }

    public MessageDTO sendMessage(Long senderId, String recipientUsername, String content) {
        User recipient = userRepository.findByUsernameAndIsActiveTrue(recipientUsername)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));
        Conversation conversation = getOrCreatePrivateConversation(senderId, recipient.getId());
        Message message = Message.builder()
                .conversation(conversation)
                .senderId(senderId)
                .content(content)
                .messageType("TEXT")
                .sentAt(LocalDateTime.now())
                .isDeleted(false)
                .status("SENT")
                .build();
        Message savedMessage = messageRepository.save(message);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        MessageDTO messageDTO = convertMessageToDTO(savedMessage);

        ChatMessageDTO chatMessage = ChatMessageDTO.builder()
                .content(content)
                .senderUsername(userRepository.findById(senderId).get().getUsername())
                .messageType("TEXT")
                .sentAt(LocalDateTime.now())
                .build();
        messagingTemplate.convertAndSend("/topic/conversation/" + conversation.getId(), chatMessage);
        return messageDTO;
    }

    public Page<MessageDTO> getConversationMessages(Long conversationId, Pageable pageable) {
        Page<Message> messagePage = messageRepository.findByConversation_IdAndIsDeletedFalse(conversationId, pageable);
        return messagePage.map(this::convertMessageToDTO);
    }

    private UserDTO convertUserToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .isOnline(user.getIsOnline())
                .build();
    }

    private Conversation getOrCreatePrivateConversation(Long user1Id, Long user2Id) {
        return conversationRepository.findPrivateConversationBetweenUsers(user1Id, user2Id)
                .orElseGet(() -> {
                    Conversation conversation = Conversation.builder()
                            .conversationType("PRIVATE")
                            .createdBy(user1Id)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    Conversation savedConversation = conversationRepository.save(conversation);
                    addParticipantToConversation(savedConversation.getId(), user1Id);
                    addParticipantToConversation(savedConversation.getId(), user2Id);
                    return savedConversation;
                });
    }

    private void addParticipantToConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        ConversationParticipant participant = ConversationParticipant.builder()
                .conversation(conversation)
                .userId(userId)
                .joinedAt(LocalDateTime.now())
                .isActive(true)
                .build();
        participantRepository.save(participant);
    }

    // Placeholder methods
    public MessageDTO sendMessageWithAttachment(Long senderId, String recipientUsername, String content, MultipartFile file) {
        return new MessageDTO();
    }

    public void sendTypingIndicator(Long senderId, String recipientUsername, boolean isTyping) {
        // Implementation here
    }
}