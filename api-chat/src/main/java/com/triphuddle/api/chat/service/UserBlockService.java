package com.triphuddle.api.chat.service;

import com.triphuddle.api.chat.dto.BlockUserRequest;
import com.triphuddle.api.chat.dto.UserBlockResponse;
import com.triphuddle.api.chat.entity.UserBlock;
import com.triphuddle.api.chat.mapper.UserBlockMapper;
import com.triphuddle.api.chat.repository.UserBlockRepository;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Lets users block others from private chats with them, and lift those blocks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final ChatUserService chatUserService;
    private final UserBlockMapper userBlockMapper;

    /**
     * Block a user; blocking someone already blocked changes nothing.
     *
     * @param blockerId the ID of the caller
     * @param request the user to block
     * @return the block
     */
    public UserBlockResponse block(UUID blockerId, BlockUserRequest request) {
        UUID blockedId = request.getUserId();
        if (blockedId.equals(blockerId)) {
            throw new IllegalStateException("You cannot block yourself");
        }
        if (!chatUserService.isActive(blockedId)) {
            throw new ResourceNotFoundException("User with id=%s not found".formatted(blockedId));
        }

        // A lifted block is reinstated rather than duplicated: the pair is unique.
        UserBlock userBlock = userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseGet(() -> {
                    UserBlock created = new UserBlock();
                    created.setBlockerId(blockerId);
                    created.setBlockedId(blockedId);
                    return created;
                });
        userBlock.setDeletedAt(null);

        UserBlock saved = userBlockRepository.saveAndFlush(userBlock);
        log.info("User blocked [blockerId={}, blockedId={}]", blockerId, blockedId);
        return userBlockMapper.toResponse(saved);
    }

    /**
     * Lift the caller's block on a user.
     *
     * @param blockerId the ID of the caller
     * @param blockedId the ID of the blocked user
     */
    public void unblock(UUID blockerId, UUID blockedId) {
        UserBlock userBlock = userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Block on user with id=%s not found".formatted(blockedId)));
        userBlock.setDeletedAt(Instant.now());
        userBlockRepository.save(userBlock);
        log.info("User unblocked [blockerId={}, blockedId={}]", blockerId, blockedId);
    }

    /**
     * Get the users the caller blocks.
     *
     * @param blockerId the ID of the caller
     * @param filter pagination parameters
     * @return a page of blocks
     */
    @Transactional(readOnly = true)
    public Paginated<UserBlockResponse> getMine(UUID blockerId, PaginationFilter filter) {
        return Paginated.of(
                userBlockRepository.findByBlockerIdAndDeletedAtIsNull(blockerId, filter.toPageable()),
                userBlockMapper::toResponseList
        );
    }
}
