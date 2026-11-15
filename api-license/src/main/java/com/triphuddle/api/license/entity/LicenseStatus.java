package com.triphuddle.api.license.entity;

/**
 * Where a license is in its lifecycle: only PUBLISHED licenses take new subscriptions.
 */
public enum LicenseStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
