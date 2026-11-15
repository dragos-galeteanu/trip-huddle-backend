package com.triphuddle.amqp.license;

/**
 * Who a license is bought for, a single user or a whole group; also the type of a subscription's subscriber.
 * Shared by the services that sell and bill licenses, and the events between them.
 */
public enum LicenseTarget {
    USER,
    GROUP
}
