package com.triphuddle.amqp.license;

import java.util.Map;
import java.util.UUID;

/**
 * Published when staff replace a license's entitlements, which apply to its subscribers at once.
 *
 * @param licenseId the ID of the license
 * @param licenseCode the code of the license
 * @param entitlements what the license grants now, by key; a null value is a flag
 */
public record LicenseEntitlementsChangedEvent(
        UUID licenseId,
        String licenseCode,
        Map<String, String> entitlements
) {
}
