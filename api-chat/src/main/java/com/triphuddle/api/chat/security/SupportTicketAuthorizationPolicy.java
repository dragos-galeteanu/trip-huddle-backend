package com.triphuddle.api.chat.security;

import com.triphuddle.api.chat.dto.CreateSupportTicketRequest;
import com.triphuddle.api.chat.dto.UpdateSupportTicketRequest;
import com.triphuddle.api.chat.entity.SupportTicket;
import com.triphuddle.api.chat.repository.SupportTicketRepository;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Customers open and follow their own tickets; Support and Admin work the queue and handle any ticket.
 */
@Component("SupportTicketPolicy")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportTicketAuthorizationPolicy
        implements BaseAuthorizationPolicy<CreateSupportTicketRequest, UpdateSupportTicketRequest, UUID> {

    private final SupportTicketRepository supportTicketRepository;

    @Override
    public Optional<UUID> resolveOwnerUserId(UUID id) {
        return supportTicketRepository.findByIdAndDeletedAtIsNull(id).map(SupportTicket::getRequesterId);
    }

    @Override
    public boolean canCreate(Authentication authentication, CreateSupportTicketRequest request) {
        // Opening a ticket towards another user is outreach by staff.
        return request.getUserId() == null ? isAuthenticated(authentication) : isAdminOrSupport(authentication);
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return isAdminOrSupport(authentication) || isResourceOwner(authentication, id);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return isAdminOrSupport(authentication);
    }

    /**
     * Check if the user can list their own tickets.
     *
     * @param authentication the authentication object
     * @return true if the user is signed in
     */
    public boolean canGetMine(Authentication authentication) {
        return isAuthenticated(authentication);
    }

    @Override
    public boolean canUpdate(Authentication authentication, UpdateSupportTicketRequest request) {
        return isAdminOrSupport(authentication);
    }

    /**
     * Check if the user can change a ticket's status; the service limits what the customer may change it to.
     *
     * @param authentication the authentication object
     * @param id the ID of the ticket
     * @return true if the user is staff or the ticket's customer
     */
    public boolean canChangeStatus(Authentication authentication, UUID id) {
        return isAdminOrSupport(authentication) || isResourceOwner(authentication, id);
    }

    /**
     * Check if the user can rate a ticket's support: only its customer.
     *
     * @param authentication the authentication object
     * @param id the ID of the ticket
     * @return true if the user is the ticket's customer
     */
    public boolean canRate(Authentication authentication, UUID id) {
        return isResourceOwner(authentication, id);
    }

    /**
     * Check if the user can claim, join or leave a ticket, or read its history.
     *
     * @param authentication the authentication object
     * @return true if the user is Support or Admin
     */
    public boolean canHandle(Authentication authentication) {
        return isAdminOrSupport(authentication);
    }

    /**
     * Check if the user can release a ticket's assignment: its assignee, or any Admin.
     *
     * @param authentication the authentication object
     * @param id the ID of the ticket
     * @return true if the user may release the ticket
     */
    public boolean canRelease(Authentication authentication, UUID id) {
        if (isAdmin(authentication)) {
            return true;
        }
        return isAdminOrSupport(authentication) && supportTicketRepository.findByIdAndDeletedAtIsNull(id)
                .map(SupportTicket::getAssigneeId)
                .map(assigneeId -> isCurrentUser(authentication, assigneeId))
                .orElse(false);
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return false;
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return false;
    }
}
