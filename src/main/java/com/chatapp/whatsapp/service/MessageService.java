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
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageAttachmentRepository attachmentRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    // File storage paths
    private final String PICTURE_DIR = "root/picture/";
    private final String VIDEO_DIR = "root/video/";
    private final String DOCUMENT_DIR = "root/documents/";
    private final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * Search users by username (fuzzy search)
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
    public MessageDTO sendMessage(Long senderId, String recipientUsername, String content) {
        // Find recipient user
        User recipient = userRepository.findByUsernameAndIsActiveTrue(recipientUsername)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        // Get or create conversation
        Conversation conversation = getOrCreatePrivateConversation(senderId, recipient.getId());

        // Create message
        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setContent(content);
        message.setMessageType("TEXT");
        message.setSentAt(LocalDateTime.now());
        message.setIsDeleted(false);
        message.setStatus("SENT");

        Message savedMessage = messageRepository.save(message);

        // Update conversation timestamp
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        // Convert to DTO
        MessageDTO messageDTO = convertMessageToDTO(savedMessage);

        // Send via WebSocket
        ChatMessageDTO chatMessage = ChatMessageDTO.builder()
            .content(content)
            .senderUsername(userRepository.findById(senderId).get().getUsername())
            .messageType("TEXT")
            .sentAt(LocalDateTime.now())
            .build();

        messagingTemplate.convertAndSend("/topic/conversation/" + conversation.getId(), chatMessage);

        return messageDTO;
    }

    /**
     * Send message with file attachment
     */
    public MessageDTO sendMessageWithAttachment(Long senderId, String recipientUsername,
                                                String content, MultipartFile file) throws IOException {
        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds 10MB limit");
        }

        // Find recipient user
        User recipient = userRepository.findByUsernameAndIsActiveTrue(recipientUsername)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        // Get or create conversation
        Conversation conversation = getOrCreatePrivateConversation(senderId, recipient.getId());

        // Save file
        String fileName = saveFile(file);

        // Create message
        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setContent(content != null ? content : "");
        message.setMessageType(getMessageTypeFromFile(file));
        message.setSentAt(LocalDateTime.now());
        message.setIsDeleted(false);
        message.setStatus("SENT");

        Message savedMessage = messageRepository.save(message);

        // Create attachment record
        MessageAttachment attachment = new MessageAttachment();
        attachment.setMessageId(savedMessage.getId());
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFilePath(fileName);
        attachment.setFileSize(file.getSize());
        attachment.setMimeType(file.getContentType());
        attachment.setUploadedAt(LocalDateTime.now());

        attachmentRepository.save(attachment);

        // Update conversation timestamp
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        // Convert to DTO
        MessageDTO messageDTO = convertMessageToDTO(savedMessage);

        // Send via WebSocket
        ChatMessageDTO chatMessage = ChatMessageDTO.builder()
            .content(content != null ? content : "📎 " + file.getOriginalFilename())
            .senderUsername(userRepository.findById(senderId).get().getUsername())
            .messageType("TEXT")
            .sentAt(LocalDateTime.now())
            .build();

        messagingTemplate.convertAndSend("/topic/conversation/" + conversation.getId(), chatMessage);

        return messageDTO;
    }

    /**
     * Get messages for a conversation with pagination
     */
    public Page<MessageDTO> getConversationMessages(Long conversationId, Pageable pageable) {
        Page<Message> messagePage = messageRepository.findByConversationIdAndIsDeletedFalse(
                conversationId, pageable);

        return messagePage.map(this::convertMessageToDTO);
    }

    /**
     * Send typing indicator
     */
    public void sendTypingIndicator(Long senderId, String recipientUsername, boolean isTyping) {
        User recipient = userRepository.findByUsernameAndIsActiveTrue(recipientUsername)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        Optional<Conversation> conversation = conversationRepository
                .findPrivateConversationBetweenUsers(senderId, recipient.getId());

        if (conversation.isPresent()) {
            User sender = userRepository.findById(senderId).orElseThrow(() -> new RuntimeException("Sender not found"));

            ChatMessageDTO typingMessage = ChatMessageDTO.builder()
                .senderId(senderId)
                .senderUsername(sender.getUsername())
                .messageType(isTyping ? "TYPING_START" : "TYPING_STOP")
                .sentAt(LocalDateTime.now())
                .build();

            messagingTemplate.convertAndSend("/topic/conversation/" + conversation.get().getId(),
                    typingMessage);
        }
    }

    /**
     * Send message via WebSocket
     */
    public MessageDTO sendMessage(ChatMessageDTO chatMessage) {
        // If conversation ID is provided, use it
        if (chatMessage.getConversationId() != null) {
            // Create message
            Message message = new Message();
            message.setConversationId(chatMessage.getConversationId());
            message.setSenderId(chatMessage.getSenderId());
            message.setContent(chatMessage.getContent());
            message.setMessageType(chatMessage.getMessageType() != null ? chatMessage.getMessageType() : "TEXT");
            message.setSentAt(LocalDateTime.now());
            message.setIsDeleted(false);
            message.setStatus("SENT");

            Message savedMessage = messageRepository.save(message);

            // Update conversation timestamp
            Conversation conversation = conversationRepository.findById(chatMessage.getConversationId())
                    .orElseThrow(() -> new RuntimeException("Conversation not found"));
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conversation);

            // Convert to DTO
            return convertMessageToDTO(savedMessage);
        } else if (chatMessage.getRecipientUsername() != null) {
            // If recipient username is provided, find or create conversation
            return sendMessage(chatMessage.getSenderId(), chatMessage.getRecipientUsername(), chatMessage.getContent());
        } else {
            throw new RuntimeException("Either conversationId or recipientUsername must be provided");
        }
    }

    /**
     * Get or create private conversation between two users
     */
    private Conversation getOrCreatePrivateConversation(Long user1Id, Long user2Id) {
        // Check if conversation already exists
        Optional<Conversation> existingConversation =
                conversationRepository.findPrivateConversationBetweenUsers(user1Id, user2Id);

        if (existingConversation.isPresent()) {
            return existingConversation.get();
        }

        // Create new conversation
        Conversation conversation = new Conversation();
        conversation.setConversationType("PRIVATE");
        conversation.setCreatedBy(user1Id);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());

        Conversation savedConversation = conversationRepository.save(conversation);

        // Add participants
        addParticipantToConversation(savedConversation.getId(), user1Id);
        addParticipantToConversation(savedConversation.getId(), user2Id);

        return savedConversation;
    }

    /**
     * Add participant to conversation
     */
    private void addParticipantToConversation(Long conversationId, Long userId) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.setConversationId(conversationId);
        participant.setUserId(userId);
        participant.setJoinedAt(LocalDateTime.now());
        participant.setIsActive(true);

        participantRepository.save(participant);
    }

    /**
     * Save uploaded file
     */
    private String saveFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString() + extension;

        String directory = getDirectoryForFile(file);
        Path directoryPath = Paths.get(directory);

        // Create directory if it doesn't exist
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
        }

        Path filePath = directoryPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return directory + fileName;
    }

    /**
     * Get directory based on file type
     */
    private String getDirectoryForFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            if (contentType.startsWith("image/")) {
                return PICTURE_DIR;
            } else if (contentType.startsWith("video/")) {
                return VIDEO_DIR;
            }
        }
        return DOCUMENT_DIR;
    }

    /**
     * Get message type from file
     */
    private String getMessageTypeFromFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            if (contentType.startsWith("image/")) {
                return "IMAGE";
            } else if (contentType.startsWith("video/")) {
                return "VIDEO";
            } else if (contentType.startsWith("audio/")) {
                return "AUDIO";
            }
        }
        return "DOCUMENT";
    }

    /**
     * Convert Message entity to DTO
     */
    private MessageDTO convertMessageToDTO(Message message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversationId());
        dto.setSenderId(message.getSenderId());
        dto.setContent(message.getContent());
        dto.setMessageType(message.getMessageType());
        dto.setSentAt(message.getSentAt());
        dto.setIsDeleted(message.getIsDeleted());
        dto.setStatus(message.getStatus());

        // Get sender info
        User sender = userRepository.findById(message.getSenderId()).orElse(null);
        if (sender != null) {
            dto.setSenderUsername(sender.getUsername());
        }

        return dto;
    }

    /**
     * Convert User entity to DTO
     */
    private UserDTO convertUserToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setDisplayName(user.getDisplayName());
        dto.setIsOnline(user.getIsOnline());
        return dto;
    }
}
