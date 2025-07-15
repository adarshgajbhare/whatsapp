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
@Transactional
@Slf4j // Add logging for the service layer
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageAttachmentRepository attachmentRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    // ... (All your existing methods like searchUsers, sendMessageWithAttachment, getConversationMessages etc. can remain here) ...

    // ----------- THE NEW, CORRECTED METHOD FOR WEBSOCKETS -----------
    /**
     * This method is specifically for saving messages coming from the WebSocket controller.
     * It is simple, direct, and logs its actions.
     */
    public MessageDTO saveWebSocketMessage(ChatMessageDTO chatMessage) {
        log.info("MessageService: Attempting to save WebSocket message. Conversation ID: {}, Sender ID: {}",
                chatMessage.getConversationId(), chatMessage.getSenderId());

        // 1. Find the conversation, or throw an error if it doesn't exist.
        Conversation conversation = conversationRepository.findById(chatMessage.getConversationId())
                .orElseThrow(() -> new RuntimeException("CRITICAL: Conversation with ID " + chatMessage.getConversationId() + " not found. Cannot save message."));

        // 2. Build the Message entity to be saved.
        Message messageToSave = Message.builder()
                .conversation(conversation)
                .senderId(chatMessage.getSenderId())
                .content(chatMessage.getContent())
                .messageType(chatMessage.getMessageType() != null ? chatMessage.getMessageType() : "TEXT")
                .sentAt(LocalDateTime.now())
                .isDeleted(false)
                .isEdited(false)
                .status("SENT")
                .build();

        // 3. Save the message to the database. This runs the INSERT query.
        Message savedMessage = messageRepository.save(messageToSave);
        log.info("MessageService: Successfully saved message with new ID: {}", savedMessage.getId());

        // 4. Update the conversation's 'updatedAt' timestamp to keep chats sorted.
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        log.info("MessageService: Updated 'updated_at' timestamp for conversation ID: {}", conversation.getId());

        // 5. Convert the saved entity to a DTO and return it.
        return convertMessageToDTO(savedMessage);
    }


    // ----------- ALL OTHER METHODS CAN REMAIN AS THEY ARE -----------

    public List<UserDTO> searchUsers(String username, Long currentUserId) {
        List<User> users = userRepository.findByUsernameContainingIgnoreCaseAndIdNotAndIsActiveTrue(username, currentUserId);
        return users.stream().map(this::convertUserToDTO).collect(Collectors.toList());
    }

    public Optional<UserDTO> findUserByUsername(String username) {
        return userRepository.findByUsernameAndIsActiveTrue(username).map(this::convertUserToDTO);
    }

    // This original method is for the REST API and is fine
    public MessageDTO sendMessage(Long senderId, String recipientUsername, String content) {
        User recipient = userRepository.findByUsernameAndIsActiveTrue(recipientUsername).orElseThrow(() -> new RuntimeException("Recipient not found"));
        Conversation conversation = getOrCreatePrivateConversation(senderId, recipient.getId());
        Message message = Message.builder().conversation(conversation).senderId(senderId).content(content).messageType("TEXT").sentAt(LocalDateTime.now()).isDeleted(false).status("SENT").build();
        Message savedMessage = messageRepository.save(message);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        MessageDTO messageDTO = convertMessageToDTO(savedMessage);
        ChatMessageDTO chatMessage = ChatMessageDTO.builder().content(content).senderUsername(userRepository.findById(senderId).get().getUsername()).messageType("TEXT").sentAt(LocalDateTime.now()).build();
        messagingTemplate.convertAndSend("/topic/conversation/" + conversation.getId(), chatMessage);
        return messageDTO;
    }

    public Page<MessageDTO> getConversationMessages(Long conversationId, Pageable pageable) {
        Page<Message> messagePage = messageRepository.findByConversation_IdAndIsDeletedFalse(conversationId, pageable);
        return messagePage.map(this::convertMessageToDTO);
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
        return UserDTO.builder().id(user.getId()).username(user.getUsername()).email(user.getEmail()).displayName(user.getDisplayName()).isOnline(user.getIsOnline()).build();
    }

    private Conversation getOrCreatePrivateConversation(Long user1Id, Long user2Id) {
        return conversationRepository.findPrivateConversationBetweenUsers(user1Id, user2Id).orElseGet(() -> {
            Conversation conversation = Conversation.builder().conversationType("PRIVATE").createdBy(user1Id).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            Conversation savedConversation = conversationRepository.save(conversation);
            addParticipantToConversation(savedConversation.getId(), user1Id);
            addParticipantToConversation(savedConversation.getId(), user2Id);
            return savedConversation;
        });
    }

    private void addParticipantToConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId).orElseThrow(() -> new RuntimeException("Conversation not found"));
        ConversationParticipant participant = ConversationParticipant.builder().conversation(conversation).userId(userId).joinedAt(LocalDateTime.now()).isActive(true).build();
        participantRepository.save(participant);
    }

    // Other methods...
    public MessageDTO sendMessageWithAttachment(Long senderId, String recipientUsername, String content, MultipartFile file) {return new MessageDTO();}
    public void sendTypingIndicator(Long senderId, String recipientUsername, boolean isTyping) {}
}