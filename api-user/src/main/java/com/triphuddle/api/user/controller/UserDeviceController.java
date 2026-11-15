package com.triphuddle.api.user.controller;

import com.triphuddle.api.user.dto.RegisterUserDeviceRequest;
import com.triphuddle.api.user.dto.UnregisterUserDeviceRequest;
import com.triphuddle.api.user.service.UserDeviceService;
import com.triphuddle.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for the caller's push devices under {@code /api/v1/users/me/devices}.
 */
@RestController
@RequestMapping("/api/v1/users/me/devices")
@RequiredArgsConstructor
@Tag(name = "User devices")
public class UserDeviceController {

    private final UserDeviceService userDeviceService;

    /**
     * Register the caller's device for push notifications; registering a token again is harmless.
     *
     * @param request the Firebase Cloud Messaging token and platform of the device
     * @param authentication the caller
     * @return accepted: the notification service stores the token shortly after
     */
    @PostMapping
    @Operation(summary = "Register a device for push notifications")
    @PreAuthorize("@UserDevicePolicy.canCreate(authentication, #request)")
    public ResponseEntity<Void> register(
            @Valid @RequestBody RegisterUserDeviceRequest request, Authentication authentication) {
        userDeviceService.register(SecurityUtils.extractUserId(authentication), request);
        return ResponseEntity.accepted().build();
    }

    /**
     * Stop push notifications on one of the caller's devices, as on sign-out.
     *
     * @param request the token of the device
     * @param authentication the caller
     * @return accepted: the notification service forgets the token shortly after
     */
    @PostMapping("/unregister")
    @Operation(summary = "Unregister a device from push notifications")
    @PreAuthorize("@UserDevicePolicy.canUpdate(authentication, #request)")
    public ResponseEntity<Void> unregister(
            @Valid @RequestBody UnregisterUserDeviceRequest request, Authentication authentication) {
        userDeviceService.unregister(SecurityUtils.extractUserId(authentication), request);
        return ResponseEntity.accepted().build();
    }
}
