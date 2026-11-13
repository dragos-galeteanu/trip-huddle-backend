package com.triphuddle.common.security;

/**
 * Platform-wide roles, carried in the Firebase {@code role} claim. Customer is the default;
 * Sales, Inspector, Support and Admin are staff roles.
 */
public enum UserRole {
    Customer,
    Sales,
    Inspector,
    Support,
    Admin
}
