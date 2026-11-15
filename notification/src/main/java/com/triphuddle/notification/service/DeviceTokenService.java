package com.triphuddle.notification.service;

import com.triphuddle.notification.entity.DeviceToken;
import com.triphuddle.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Keeps the push devices of users, as api-user reports them registered and unregistered.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * Register a device for a user; a token already registered moves to this user.
     *
     * @param userId the ID of the user
     * @param token the Firebase Cloud Messaging token
     * @param platform the platform of the device
     */
    public void register(UUID userId, String token, String platform) {
        DeviceToken deviceToken = deviceTokenRepository.findByToken(token).orElseGet(DeviceToken::new);
        deviceToken.setToken(token);
        deviceToken.setUserId(userId);
        deviceToken.setPlatform(platform);
        deviceTokenRepository.save(deviceToken);
        log.info("Device token registered [userId={}, platform={}]", userId, platform);
    }

    /**
     * Forget a device of a user; a token now registered to another user is left alone.
     *
     * @param userId the ID of the user
     * @param token the Firebase Cloud Messaging token
     */
    public void unregister(UUID userId, String token) {
        deviceTokenRepository.deleteByUserIdAndToken(userId, token);
        log.info("Device token unregistered [userId={}]", userId);
    }
}
