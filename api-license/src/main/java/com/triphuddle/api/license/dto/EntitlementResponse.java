package com.triphuddle.api.license.dto;

/**
 * One entitlement of a license.
 *
 * @param key the entitlement key, such as {@code MAX_GROUPS}
 * @param value the value, or null if the entitlement is a plain flag
 */
public record EntitlementResponse(String key, String value) {
}
