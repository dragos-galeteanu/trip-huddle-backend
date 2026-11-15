package com.triphuddle.api.chat.security;

import com.triphuddle.api.chat.dto.CreateMessageReportRequest;
import com.triphuddle.api.chat.dto.ResolveMessageReportRequest;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Anyone who can read a message may report it; Admin, Support and Inspector review the reports.
 */
@Component("MessageReportPolicy")
@RequiredArgsConstructor
public class MessageReportAuthorizationPolicy
        implements BaseAuthorizationPolicy<CreateMessageReportRequest, ResolveMessageReportRequest, UUID> {

    private final ChatAccess chatAccess;

    @Override
    public boolean canCreate(Authentication authentication, CreateMessageReportRequest request) {
        // The message is in the route, not the body: controllers ask canReport instead.
        return false;
    }

    /**
     * Check if the user can report a message.
     *
     * @param authentication the authentication object
     * @param messageId the ID of the message
     * @return true if the user can read the message
     */
    public boolean canReport(Authentication authentication, String messageId) {
        return chatAccess.canReadMessage(authentication, messageId);
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        return isAdminOrSupportOrInspector(authentication);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    @Override
    public boolean canUpdate(Authentication authentication, ResolveMessageReportRequest request) {
        return isAdminOrSupportOrInspector(authentication);
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return false;
    }
}
