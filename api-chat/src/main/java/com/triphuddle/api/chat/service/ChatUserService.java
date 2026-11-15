package com.triphuddle.api.chat.service;

import com.triphuddle.api.chat.entity.ChatUser;
import com.triphuddle.api.chat.repository.ChatUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Keeps the list of platform users from api-user's events: registered, disabled and deleted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatUserService {

    private final ChatUserRepository chatUserRepository;

    /**
     * Record a newly registered user; a repeated event changes nothing.
     *
     * @param userId the ID of the user
     */
    public void register(UUID userId) {
        if (chatUserRepository.findByUserId(userId).isPresent()) {
            log.debug("Chat user already recorded [userId={}]", userId);
            return;
        }
        ChatUser chatUser = new ChatUser();
        chatUser.setUserId(userId);
        chatUserRepository.save(chatUser);
        log.info("Chat user recorded [userId={}]", userId);
    }

    /**
     * Mark a user as disabled, recording them first if their registration was never seen.
     *
     * @param userId the ID of the user
     */
    public void disable(UUID userId) {
        ChatUser chatUser = findOrNew(userId);
        if (chatUser.getDisabledAt() == null) {
            chatUser.setDisabledAt(Instant.now());
        }
        chatUserRepository.save(chatUser);
        log.info("Chat user disabled [userId={}]", userId);
    }

    /**
     * Mark a user as deleted, recording them first if their registration was never seen.
     *
     * @param userId the ID of the user
     */
    public void delete(UUID userId) {
        ChatUser chatUser = findOrNew(userId);
        if (chatUser.getDeletedAt() == null) {
            chatUser.setDeletedAt(Instant.now());
        }
        chatUserRepository.save(chatUser);
        log.info("Chat user deleted [userId={}]", userId);
    }

    /**
     * Check if a user is known and can take part in chats.
     *
     * @param userId the ID of the user
     * @return true if the user is known and neither disabled nor deleted
     */
    @Transactional(readOnly = true)
    public boolean isActive(UUID userId) {
        return chatUserRepository.existsByUserIdAndDeletedAtIsNullAndDisabledAtIsNull(userId);
    }

    /**
     * Find which of the given users are known to be disabled or deleted.
     *
     * @param userIds the IDs of the users
     * @return the IDs of those that are disabled or deleted
     */
    @Transactional(readOnly = true)
    public Set<UUID> findInactive(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return chatUserRepository.findInactiveAmong(userIds).stream()
                .map(ChatUser::getUserId)
                .collect(Collectors.toSet());
    }

    private ChatUser findOrNew(UUID userId) {
        return chatUserRepository.findByUserId(userId).orElseGet(() -> {
            ChatUser chatUser = new ChatUser();
            chatUser.setUserId(userId);
            return chatUser;
        });
    }
}
