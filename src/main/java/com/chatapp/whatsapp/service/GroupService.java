package com.chatapp.whatsapp.service;

import com.chatapp.whatsapp.dto.CreateGroupRequest;
import com.chatapp.whatsapp.entity.Conversation;
import com.chatapp.whatsapp.entity.ConversationParticipant;
import com.chatapp.whatsapp.respository.ConversationParticipantRepository;
import com.chatapp.whatsapp.respository.ConversationRepository;
import com.chatapp.whatsapp.respository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;

    public Conversation createGroup(CreateGroupRequest request) {
        // Validate that all users exist
        List<Long> allUserIds = new ArrayList<>(request.getMemberIds());
        allUserIds.add(request.getCreatorId());
        if (userRepository.countByIdIn(allUserIds) != allUserIds.size()) {
            throw new RuntimeException("One or more users not found");
        }

        // Create new group conversation
        Conversation groupConversation = Conversation.builder()
                .name(request.getName())
                .conversationType("GROUP")
                .createdBy(request.getCreatorId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Conversation savedConversation = conversationRepository.save(groupConversation);

        // Add creator as ADMIN
        ConversationParticipant creatorParticipant = ConversationParticipant.builder()
                .conversation(savedConversation)
                .userId(request.getCreatorId())
                .role("ADMIN")
                .isActive(true)
                .joinedAt(LocalDateTime.now())
                .build();
        participantRepository.save(creatorParticipant);

        // Add other members
        for (Long memberId : request.getMemberIds()) {
            ConversationParticipant memberParticipant = ConversationParticipant.builder()
                    .conversation(savedConversation)
                    .userId(memberId)
                    .role("MEMBER")
                    .isActive(true)
                    .joinedAt(LocalDateTime.now())
                    .build();
            participantRepository.save(memberParticipant);
        }

        return savedConversation;
    }

    public void addParticipant(Long conversationId, Long userIdToAdd, Long requestingUserId) {
        // Check if conversation exists and is a group
        Conversation conversation = conversationRepository.findById(conversationId)
                .filter(Conversation::isGroup)
                .orElseThrow(() -> new RuntimeException("Group conversation not found"));

        // Check if the requesting user is an ADMIN
        participantRepository.findByConversationIdAndUserId(conversationId, requestingUserId)
                .filter(p -> p.getIsActive() && p.isAdmin())
                .orElseThrow(() -> new SecurityException("User does not have permission to add participants"));

        // Check if user to be added exists
        userRepository.findById(userIdToAdd)
                .orElseThrow(() -> new RuntimeException("User to add not found"));

        // Check if user is already a participant
        if (participantRepository.existsByConversationIdAndUserId(conversationId, userIdToAdd)) {
            throw new RuntimeException("User is already a member of this group");
        }

        // Add the new participant
        ConversationParticipant newParticipant = ConversationParticipant.builder()
                .conversation(conversation)
                .userId(userIdToAdd)
                .role("MEMBER")
                .isActive(true)
                .joinedAt(LocalDateTime.now())
                .build();
        participantRepository.save(newParticipant);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    public void removeParticipant(Long conversationId, Long userIdToRemove, Long requestingUserId) {
        // Check if conversation exists and is a group
        Conversation conversation = conversationRepository.findById(conversationId)
                .filter(Conversation::isGroup)
                .orElseThrow(() -> new RuntimeException("Group conversation not found"));

        // Check if the requesting user is an ADMIN
        participantRepository.findByConversationIdAndUserId(conversationId, requestingUserId)
                .filter(p -> p.getIsActive() && p.isAdmin())
                .orElseThrow(() -> new SecurityException("User does not have permission to remove participants"));

        // Find the participant to remove
        ConversationParticipant participantToRemove = participantRepository.findByConversationIdAndUserId(conversationId, userIdToRemove)
                .filter(ConversationParticipant::getIsActive)
                .orElseThrow(() -> new RuntimeException("Participant not found in this group"));

        // Deactivate the participant (soft delete)
        participantToRemove.leave();
        participantRepository.save(participantToRemove);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }
}