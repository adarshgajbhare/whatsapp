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
    private final String UPLOAD_DIR = "uploads";
    private final String PICTURE_DIR = UPLOAD_DIR + "/pictures/";
    private final String VIDEO_DIR = UPLOAD_DIR + "/videos/";
    private final String DOCUMENT_DIR = UPLOAD_DIR + "/documents/";
    private final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public List<UserDTO> searchUsers(String username, Long currentUserId) {
        List<User> users = userRepository.findByUsernameContainingIgnoreCaseAndIdNotAndIsActiveTrue(
                username, currentUserId);
        return users.stream()
                .map(this::convertUserToDTO)
                .collect(Collectors.toList());
    }

    public Optional<UserDTO> findUserByUsername(String username) {
        return userRepository.findByUsernameAndIsActiveTrue(username)
                .map(this::convertUserToDTO);
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

    public MessageDTO sendMessageWithAttachment(Long senderId, String recipientUsername,
                                                String content, MultipartFile file) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size exceeds 10MB limit");
        }
        User recipient = userRepository.findByUsernameAndIsActiveTrue(recipientUsername)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));
        Conversation conversation = getOrCreatePrivateConversation(senderId, recipient.getId());
        String filePath = saveFile(file); // Corrected variable name for clarity
        Message message = Message.builder()
                .conversation(conversation)
                .senderId(senderId)
                .content(content != null ? content : "")
                .messageType(getMessageTypeFromFile(file))
                .sentAt(LocalDateTime.now())
                .isDeleted(false)
                .status("SENT")
                .build();
        Message savedMessage = messageRepository.save(message);
        MessageAttachment attachment = MessageAttachment.builder()
                .message(savedMessage)
                .fileName(file.getOriginalFilename())
                .filePath(filePath)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .uploadedAt(LocalDateTime.now())
                .build();
        attachmentRepository.save(attachment);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        MessageDTO messageDTO = convertMessageToDTO(savedMessage);
        ChatMessageDTO chatMessage = ChatMessageDTO.builder()
                .content(content != null ? content : "📎 " + file.getOriginalFilename())
                .senderUsername(userRepository.findById(senderId).get().getUsername())
                .messageType(getMessageTypeFromFile(file))
                .sentAt(LocalDateTime.now())
                .build();
        messagingTemplate.convertAndSend("/topic/conversation/" + conversation.getId(), chatMessage);
        return messageDTO;
    }

    public Page<MessageDTO> getConversationMessages(Long conversationId, Pageable pageable) {
        Page<Message> messagePage = messageRepository.findByConversation_IdAndIsDeletedFalse(
                conversationId, pageable);
        return messagePage.map(this::convertMessageToDTO);
    }

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

    public MessageDTO sendMessage(ChatMessageDTO chatMessage) {
        if (chatMessage.getConversationId() != null) {
            Message message = Message.builder()
                    .conversation(conversationRepository.findById(chatMessage.getConversationId()).orElseThrow())
                    .senderId(chatMessage.getSenderId())
                    .content(chatMessage.getContent())
                    .messageType(chatMessage.getMessageType() != null ? chatMessage.getMessageType() : "TEXT")
                    .sentAt(LocalDateTime.now())
                    .isDeleted(false)
                    .status("SENT")
                    .build();
            Message savedMessage = messageRepository.save(message);
            Conversation conversation = savedMessage.getConversation();
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conversation);
            return convertMessageToDTO(savedMessage);
        } else if (chatMessage.getRecipientUsername() != null) {
            return sendMessage(chatMessage.getSenderId(), chatMessage.getRecipientUsername(), chatMessage.getContent());
        } else {
            throw new RuntimeException("Either conversationId or recipientUsername must be provided");
        }
    }

    // --- HELPER METHODS (THIS IS WHAT WAS MISSING) ---

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

    private String saveFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = UUID.randomUUID().toString() + extension;
        String directory = getDirectoryForFile(file);
        Path directoryPath = Paths.get(directory);
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
        }
        Path filePath = directoryPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        // Return relative path for storage
        return directory.replace(UPLOAD_DIR + "/", "") + fileName;
    }

    private String getDirectoryForFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            if (contentType.startsWith("image/")) return PICTURE_DIR;
            if (contentType.startsWith("video/")) return VIDEO_DIR;
        }
        return DOCUMENT_DIR;
    }

    private String getMessageTypeFromFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            if (contentType.startsWith("image/")) return "IMAGE";
            if (contentType.startsWith("video/")) return "VIDEO";
            if (contentType.startsWith("audio/")) return "AUDIO";
        }
        return "DOCUMENT";
    }

    private MessageDTO convertMessageToDTO(Message message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversation().getId());
        dto.setSenderId(message.getSenderId());
        dto.setContent(message.getContent());
        dto.setMessageType(message.getMessageType());
        dto.setSentAt(message.getSentAt());
        dto.setIsDeleted(message.getIsDeleted());
        dto.setStatus(message.getStatus());
        userRepository.findById(message.getSenderId()).ifPresent(sender -> {
            dto.setSenderUsername(sender.getUsername());
            dto.setSenderDisplayName(sender.getDisplayName());
            dto.setSenderProfileImage(sender.getProfileImage());
        });
        return dto;
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
}