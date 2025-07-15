package com.chatapp.whatsapp.service;

import com.chatapp.whatsapp.entity.Conversation;
import com.chatapp.whatsapp.entity.ConversationParticipant;
import com.chatapp.whatsapp.entity.User;
import com.chatapp.whatsapp.respository.ConversationParticipantRepository;
import com.chatapp.whatsapp.respository.ConversationRepository;
import com.chatapp.whatsapp.respository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebugTestService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;

    /**
     * Test method to verify conversation and participant setup
     * Call this method to debug your group chat setup
     */
    public void verifyGroupSetup(Long conversationId, Long userId) {
        log.info("=== DEBUGGING GROUP SETUP ===");
        log.info("Checking Conversation ID: {}, User ID: {}", conversationId, userId);

        // Check if conversation exists
        Optional<Conversation> conversationOpt = conversationRepository.findById(conversationId);
        if (!conversationOpt.isPresent()) {
            log.error("❌ CONVERSATION NOT FOUND: ID {}", conversationId);
            return;
        }

        Conversation conversation = conversationOpt.get();
        log.info("✅ CONVERSATION FOUND:");
        log.info("   ID: {}", conversation.getId());
        log.info("   Type: {}", conversation.getConversationType());
        log.info("   Name: {}", conversation.getName());
        log.info("   Created By: {}", conversation.getCreatedBy());
        log.info("   Created At: {}", conversation.getCreatedAt());
        log.info("   Updated At: {}", conversation.getUpdatedAt());

        // Check if user exists
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            log.error("❌ USER NOT FOUND: ID {}", userId);
            return;
        }

        User user = userOpt.get();
        log.info("✅ USER FOUND:");
        log.info("   ID: {}", user.getId());
        log.info("   Username: {}", user.getUsername());
        log.info("   Display Name: {}", user.getDisplayName());
        log.info("   Is Active: {}", user.getIsActive());

        // Check all participants in the conversation
        List<ConversationParticipant> allParticipants = participantRepository.findByConversationId(conversationId);
        log.info("✅ ALL PARTICIPANTS IN CONVERSATION {}:", conversationId);
        log.info("   Total participants: {}", allParticipants.size());

        for (ConversationParticipant participant : allParticipants) {
            log.info("   Participant:");
            log.info("     User ID: {}", participant.getUserId());
            log.info("     Role: {}", participant.getRole());
            log.info("     Is Active: {}", participant.getIsActive());
            log.info("     Joined At: {}", participant.getJoinedAt());
            log.info("     Left At: {}", participant.getLeftAt());
        }

        // Check specific participant
        Optional<ConversationParticipant> specificParticipant = participantRepository
                .findByConversationIdAndUserId(conversationId, userId);

        if (!specificParticipant.isPresent()) {
            log.error("❌ USER {} IS NOT A PARTICIPANT IN CONVERSATION {}", userId, conversationId);
            log.error("   This is why the message is not being saved!");
            return;
        }

        ConversationParticipant participant = specificParticipant.get();
        log.info("✅ SPECIFIC PARTICIPANT FOUND:");
        log.info("   User ID: {}", participant.getUserId());
        log.info("   Role: {}", participant.getRole());
        log.info("   Is Active: {}", participant.getIsActive());
        log.info("   Joined At: {}", participant.getJoinedAt());
        log.info("   Left At: {}", participant.getLeftAt());

        // Final verification
        boolean exists = participantRepository.existsByConversationIdAndUserId(conversationId, userId);
        log.info("✅ PARTICIPANT EXISTS CHECK: {}", exists);

        if (!participant.getIsActive()) {
            log.error("❌ PARTICIPANT IS NOT ACTIVE - This is why the message is not being saved!");
        } else {
            log.info("✅ PARTICIPANT IS ACTIVE - Message should be saved successfully");
        }

        log.info("=== DEBUG COMPLETE ===");
    }

    /**
     * Test method to create a test group if needed
     */
    public void createTestGroup(Long creatorId, List<Long> memberIds, String groupName) {
        log.info("Creating test group: {} with creator: {} and members: {}",
                groupName, creatorId, memberIds);

        // Implementation here if needed
    }
}