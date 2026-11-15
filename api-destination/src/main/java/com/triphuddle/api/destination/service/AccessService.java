package com.triphuddle.api.destination.service;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.destination.config.DestinationProperties;
import com.triphuddle.api.destination.entity.LicenseAccess;
import com.triphuddle.api.destination.repository.GroupMemberRepository;
import com.triphuddle.api.destination.repository.LicenseAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Decides who may use the service: a user with the access entitlement in a license of their own, or, when acting
 * for a group, a member of a group whose license has it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessService {

    // Entitlement values that switch an entitlement off rather than on.
    private static final Set<String> OFF_VALUES = Set.of("false", "0", "no", "off");

    private final LicenseAccessRepository licenseAccessRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final DestinationProperties properties;
    private final Clock clock;

    /**
     * Check if a user may use the service, on their own or for a group.
     *
     * @param userId the ID of the user
     * @param groupId the group they act for, or null for themselves
     * @return true if a current license gives them the access entitlement
     */
    public boolean hasAccess(UUID userId, UUID groupId) {
        if (userId == null) {
            return false;
        }
        if (groupId == null) {
            return hasEntitlement(LicenseTarget.USER, userId, properties.accessEntitlement());
        }
        // A group's license counts only for its own members, and only when they act for the group.
        return groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)
                && hasEntitlement(LicenseTarget.GROUP, groupId, properties.accessEntitlement());
    }

    /**
     * Check if a subscriber has a current subscription with an entitlement switched on.
     *
     * @param subscriberType USER or GROUP
     * @param subscriberId the ID of the user or group
     * @param key the entitlement key
     * @return true if one of its current subscriptions has it
     */
    public boolean hasEntitlement(LicenseTarget subscriberType, UUID subscriberId, String key) {
        Instant after = clock.instant().minus(Duration.ofHours(properties.accessGraceHours()));
        return licenseAccessRepository.findCurrent(subscriberType, subscriberId, after).stream()
                .anyMatch(access -> isOn(access, key));
    }

    /**
     * Get the largest limit a subscriber's current subscriptions give a quota entitlement.
     *
     * @param subscriberType USER or GROUP
     * @param subscriberId the ID of the user or group
     * @param key the entitlement key, e.g. AI_ITINERARIES_PER_MONTH
     * @return the limit; 0 if no subscription has it, {@link Integer#MAX_VALUE} for {@code unlimited}
     */
    public int quotaOf(LicenseTarget subscriberType, UUID subscriberId, String key) {
        Instant after = clock.instant().minus(Duration.ofHours(properties.accessGraceHours()));
        int limit = 0;
        for (LicenseAccess access : licenseAccessRepository.findCurrent(subscriberType, subscriberId, after)) {
            String value = access.getEntitlements().get(key);
            if (value == null) {
                continue;
            }
            String text = value.strip().toLowerCase();
            if (text.equals("unlimited")) {
                return Integer.MAX_VALUE;
            }
            try {
                limit = Math.max(limit, Integer.parseInt(text));
            } catch (NumberFormatException e) {
                // A switch such as "true" gives no number; it doesn't count as a limit.
            }
        }
        return limit;
    }

    private static boolean isOn(LicenseAccess access, String key) {
        if (!access.getEntitlements().containsKey(key)) {
            return false;
        }
        String value = access.getEntitlements().get(key);
        return value == null || !OFF_VALUES.contains(value.strip().toLowerCase());
    }
}
