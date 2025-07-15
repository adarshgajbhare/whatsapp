package com.chatapp.whatsapp.service;

import com.chatapp.whatsapp.dto.ChatMessageDTO;
import com.chatapp.whatsapp.dto.MessageDTO;
import com.chatapp.whatsapp.dto.UserDTO;
import com.chatapp.whatsapp.entity.*;
import com.chatapp.whatsapp.respository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final com.chatapp.whatsapp.repository.MessageRepository messageRepository;
    private final MessageAttachmentRepository attachmentRepository;
    private final ConversationParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String UPLOAD_DIR = "root/";

    /**
     * Search users by username (excluding current user)
     */
    public List<UserDTO> searchUsers(String username, Long currentUserId) {
        List<User> users = userRepository.findByUsernameContainingIgnoreCaseAndIdNotAndIsActiveTrue(
                username, currentUserId);
        return users.stream()
                .map(this::convertUserToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Find user by exact username
     */
    public Optional<UserDTO> findUserByUsername(String username) {
        return userRepository.findByUsernameAndIsActiveTrue(username)
                .map(this::convertUserToDTO);
    }

    /**
     * Send text message between users
     */
    @Transactional
    public MessageDTO sendMessage(Long senderId, String recipientUsername, String content) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        User recipient = userRepository.findByUsernameAndIsActiveTrue(recipientUsername)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));

        // Find or create conversation
        Conversation conversation = findOrCreatePrivateConversation(sender, recipient);

        // Create message
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

        // Broadcast via WebSocket
        MessageDTO messageDTO = convertMessageToDTO(savedMessage);
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversation.getId(),
                messageDTO
        );

        return messageDTO;
    }

    /**
     * Send message with file attachment
     */
    @Transactional
    public MessageDTO sendMessageWithAttachment(Long senderId, String recipientUsername,
                                                String content, MultipartFile file) throws IOException {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        User recipient = userRepository.findByUsernameAndIsActiveTrue(recipientUsername)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));

        // Validate file size (10MB limit)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }

        // Find or create conversation
        Conversation conversation = findOrCreatePrivateConversation(sender, recipient);

        // Create message
        Message message = Message.builder()
                .conversation(conversation)
                .senderId(senderId)
                .content(content)
                .messageType("ATTACHMENT")
                .sentAt(LocalDateTime.now())
                .isDeleted(false)
                .status("SENT")
                .build();

        Message savedMessage = messageRepository.save(message);

        // Save file attachment
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        String fileType = determineFileType(file.getContentType());
        Path uploadPath = Paths.get(UPLOAD_DIR + fileType);
        Files.createDirectories(uploadPath);

        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);

        MessageAttachment attachment = MessageAttachment.builder()
                .message(savedMessage)
                .fileName(file.getOriginalFilename())
                .filePath(filePath.toString())
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .uploadedAt(LocalDateTime.now())
                .build();

        attachmentRepository.save(attachment);

        // Broadcast via WebSocket
        MessageDTO messageDTO = convertMessageToDTO(savedMessage);
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversation.getId(),
                messageDTO
        );

        return messageDTO;
    }

    /**
     * Handle WebSocket message - THE MISSING buildEntity METHOD IS HERE
     */
    @Transactional
    public MessageDTO saveWebSocketMessage(ChatMessageDTO chatMessage) {
        // Validate conversation exists
        Conversation conversation = conversationRepository.findById(chatMessage.getConversationId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        // Check if sender is participant (with auto-add fallback)
        ConversationParticipant senderParticipant =
                participantRepository
                        .findByConversationIdAndUserId(conversation.getId(), chatMessage.getSenderId())
                        .orElseGet(() -> {
                            ConversationParticipant cp = ConversationParticipant.builder()
                                    .conversation(conversation)
                                    .userId(chatMessage.getSenderId())
                                    .role("MEMBER")
                                    .isActive(true)
                                    .joinedAt(LocalDateTime.now())
                                    .build();
                            return participantRepository.save(cp);
                        });

        if (!senderParticipant.getIsActive()) {
            throw new SecurityException("Sender is not an active participant");
        }

        // Build and save message entity
        Message saved = messageRepository.save(buildEntity(chatMessage));

        // Broadcast to conversation subscribers
        MessageDTO messageDTO = convertMessageToDTO(saved);
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + saved.getConversationId(),
                messageDTO
        );

        return messageDTO;
    }

    /**
     * THE MISSING buildEntity METHOD - Converts ChatMessageDTO to Message entity
     */
    private Message buildEntity(ChatMessageDTO chatMessage) {
        Conversation conversation = conversationRepository.findById(chatMessage.getConversationId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        return Message.builder()
                .conversation(conversation)
                .senderId(chatMessage.getSenderId())
                .content(chatMessage.getContent())
                .messageType(chatMessage.getMessageType() != null ? chatMessage.getMessageType() : "TEXT")
                .sentAt(LocalDateTime.now())
                .isDeleted(false)
                .status("SENT")
                .build();
    }

    /**
     * Get conversation messages with pagination
     */
    public Page<MessageDTO> getConversationMessages(Long conversationId, Pageable pageable) {
        // Use the correct method name with relationship navigation
        Page<Message> messages = messageRepository.findByConversation_IdAndIsDeletedFalse(
                conversationId, pageable);
        return messages.map(this::convertMessageToDTO);
    }

    /**
     * Send typing indicator
     */
    public void sendTypingIndicator(Long senderId, String recipientUsername, boolean isTyping) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        User recipient = userRepository.findByUsernameAndIsActiveTrue(recipientUsername)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));

        Conversation conversation = findOrCreatePrivateConversation(sender, recipient);

        // Broadcast typing indicator
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversation.getId() + "/typing",
                new TypingIndicatorDTO(senderId, sender.getUsername(), isTyping)
        );
    }

    /**
     * Find or create private conversation between two users
     */
    private Conversation findOrCreatePrivateConversation(User user1, User user2) {
        Optional<Conversation> existingConversation = conversationRepository
                .findPrivateConversationBetweenUsers(user1.getId(), user2.getId());

        if (existingConversation.isPresent()) {
            return existingConversation.get();
        }

        // Create new private conversation
        Conversation conversation = Conversation.builder()
                .conversationType("PRIVATE")
                .createdBy(user1.getId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Conversation savedConversation = conversationRepository.save(conversation);

        // Add both users as participants
        ConversationParticipant participant1 = ConversationParticipant.builder()
                .conversation(savedConversation)
                .userId(user1.getId())
                .role("MEMBER")
                .isActive(true)
                .joinedAt(LocalDateTime.now())
                .build();

        ConversationParticipant participant2 = ConversationParticipant.builder()
                .conversation(savedConversation)
                .userId(user2.getId())
                .role("MEMBER")
                .isActive(true)
                .joinedAt(LocalDateTime.now())
                .build();

        participantRepository.save(participant1);
        participantRepository.save(participant2);

        return savedConversation;
    }

    /**
     * Convert Message entity to DTO
     */
    private MessageDTO convertMessageToDTO(Message message) {
        User sender = userRepository.findById(message.getSenderId()).orElse(null);

        return MessageDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderUsername(sender != null ? sender.getUsername() : "Unknown")
                .content(message.getContent())
                .messageType(message.getMessageType())
                .sentAt(message.getSentAt())
                .isDeleted(message.getIsDeleted())
                .status(message.getStatus())
                .build();
    }

    /**
     * Convert User entity to DTO
     */
    private UserDTO convertUserToDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .isOnline(user.getIsOnline())
                .build();
    }

    /**
     * Determine file type based on MIME type
     */
    private String determineFileType(String mimeType) {
        if (mimeType == null) return "documents";

        if (mimeType.startsWith("image/")) return "picture";
        if (mimeType.startsWith("video/")) return "video";
        if (mimeType.startsWith("audio/")) return "audio";
        return "documents";
    }

    /**
     * Simple DTO for typing indicators
     */
    public static class TypingIndicatorDTO {
        private Long senderId;
        private String senderUsername;
        private boolean isTyping;

        public TypingIndicatorDTO(Long senderId, String senderUsername, boolean isTyping) {
            this.senderId = senderId;
            this.senderUsername = senderUsername;
            this.isTyping = isTyping;
        }

        // Getters and setters
        public Long getSenderId() { return senderId; }
        public void setSenderId(Long senderId) { this.senderId = senderId; }
        public String getSenderUsername() { return senderUsername; }
        public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }
        public boolean isTyping() { return isTyping; }
        public void setTyping(boolean typing) { isTyping = typing; }
    }
}
