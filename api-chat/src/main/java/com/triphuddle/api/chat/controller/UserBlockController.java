package com.triphuddle.api.chat.controller;

import com.triphuddle.api.chat.dto.BlockUserRequest;
import com.triphuddle.api.chat.dto.UserBlockResponse;
import com.triphuddle.api.chat.service.UserBlockService;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for the caller's blocks under {@code /api/v1/chats/blocks}.
 */
@RestController
@RequestMapping("/api/v1/chats/blocks")
@RequiredArgsConstructor
@Tag(name = "Chat blocks")
public class UserBlockController {

    private final UserBlockService userBlockService;

    /**
     * Block a user from private chats with the caller.
     *
     * @param request the user to block
     * @param authentication the caller
     * @return the block
     */
    @PostMapping
    @Operation(summary = "Block a user")
    @PreAuthorize("@UserBlockPolicy.canCreate(authentication, #request)")
    public ResponseEntity<UserBlockResponse> block(
            @Valid @RequestBody BlockUserRequest request, Authentication authentication) {
        UserBlockResponse response = userBlockService.block(SecurityUtils.extractUserId(authentication), request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get the users the caller blocks.
     *
     * @param filter pagination parameters
     * @param authentication the caller
     * @return a page of blocks
     */
    @GetMapping
    @Operation(summary = "Get the users I block")
    @PreAuthorize("@UserBlockPolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<UserBlockResponse>> getMine(
            @Valid @ModelAttribute PaginationFilter filter, Authentication authentication) {
        return ResponseEntity.ok(userBlockService.getMine(SecurityUtils.extractUserId(authentication), filter));
    }

    /**
     * Lift the caller's block on a user.
     *
     * @param userId the ID of the blocked user
     * @param authentication the caller
     * @return no content
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "Unblock a user")
    @PreAuthorize("@UserBlockPolicy.canDelete(authentication, #userId)")
    public ResponseEntity<Void> unblock(@PathVariable UUID userId, Authentication authentication) {
        userBlockService.unblock(SecurityUtils.extractUserId(authentication), userId);
        return ResponseEntity.noContent().build();
    }
}
