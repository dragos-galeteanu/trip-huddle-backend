package com.triphuddle.api.license.controller;

import com.triphuddle.api.license.dto.ExtendSubscriptionRequest;
import com.triphuddle.api.license.dto.GrantSubscriptionRequest;
import com.triphuddle.api.license.dto.SubscribeRequest;
import com.triphuddle.api.license.dto.SubscriptionFilter;
import com.triphuddle.api.license.dto.SubscriptionResponse;
import com.triphuddle.api.license.service.SubscriptionService;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for subscriptions under {@code /api/v1/licenses/subscriptions}.
 */
@RestController
@RequestMapping("/api/v1/licenses/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Subscribe the caller, or a group they administer, to a license; the subscription waits for payment.
     *
     * @param request the license and an optional promotion code
     * @param authentication the caller
     * @return the pending subscription
     */
    @PostMapping
    @Operation(summary = "Subscribe to a license")
    @PreAuthorize("@SubscriptionPolicy.canCreate(authentication, #request)")
    public ResponseEntity<SubscriptionResponse> subscribe(@Valid @RequestBody SubscribeRequest request,
                                                          Authentication authentication) {
        return new ResponseEntity<>(subscriptionService.subscribe(SecurityUtils.extractUserId(authentication), request),
                HttpStatus.CREATED);
    }

    /**
     * Get the caller's own subscriptions, newest first.
     *
     * @param filter the page and the status to keep
     * @param authentication the caller
     * @return a page of subscriptions
     */
    @GetMapping("/mine")
    @Operation(summary = "Get my subscriptions")
    @PreAuthorize("@SubscriptionPolicy.canGetMine(authentication)")
    public ResponseEntity<Paginated<SubscriptionResponse>> getMine(@Valid @ModelAttribute SubscriptionFilter filter,
                                                                   Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.getMine(SecurityUtils.extractUserId(authentication), filter));
    }

    /**
     * Get the caller's active subscription, with what it grants.
     *
     * @param authentication the caller
     * @return the subscription, or 204 if the caller has none
     */
    @GetMapping("/mine/active")
    @Operation(summary = "Get my active subscription")
    @PreAuthorize("@SubscriptionPolicy.canGetMine(authentication)")
    public ResponseEntity<SubscriptionResponse> getActive(Authentication authentication) {
        return subscriptionService.getActive(SecurityUtils.extractUserId(authentication))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Get a group's subscriptions, newest first.
     *
     * @param groupId the ID of the group
     * @param filter the page and the status to keep
     * @return a page of subscriptions
     */
    @GetMapping("/groups/{groupId}")
    @Operation(summary = "Get a group's subscriptions")
    @PreAuthorize("@SubscriptionPolicy.canGetForGroup(authentication, #groupId)")
    public ResponseEntity<Paginated<SubscriptionResponse>> getForGroup(@PathVariable UUID groupId,
                                                                       @Valid @ModelAttribute SubscriptionFilter filter) {
        return ResponseEntity.ok(subscriptionService.getForGroup(groupId, filter));
    }

    /**
     * Get a group's active subscription, with what it grants.
     *
     * @param groupId the ID of the group
     * @return the subscription, or 204 if the group has none
     */
    @GetMapping("/groups/{groupId}/active")
    @Operation(summary = "Get a group's active subscription")
    @PreAuthorize("@SubscriptionPolicy.canGetForGroup(authentication, #groupId)")
    public ResponseEntity<SubscriptionResponse> getActiveForGroup(@PathVariable UUID groupId) {
        return subscriptionService.getActiveForGroup(groupId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Get subscriptions, newest first.
     *
     * @param filter the page and the filters
     * @return a page of subscriptions
     */
    @GetMapping
    @Operation(summary = "Get subscriptions")
    @PreAuthorize("@SubscriptionPolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<SubscriptionResponse>> getAll(@Valid @ModelAttribute SubscriptionFilter filter) {
        return ResponseEntity.ok(subscriptionService.getAll(filter));
    }

    /**
     * Get a subscription.
     *
     * @param id the ID of the subscription
     * @return the subscription
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a subscription")
    @PreAuthorize("@SubscriptionPolicy.canGetById(authentication, #id)")
    public ResponseEntity<SubscriptionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(subscriptionService.getById(id));
    }

    /**
     * Cancel a subscription: a pending one ends, an active one stops renewing.
     *
     * @param id the ID of the subscription
     * @param authentication the caller
     * @return the canceled subscription
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a subscription")
    @PreAuthorize("@SubscriptionPolicy.canChangeRenewal(authentication, #id)")
    public ResponseEntity<SubscriptionResponse> cancel(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.cancel(id, SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Undo the cancel of an active subscription.
     *
     * @param id the ID of the subscription
     * @param authentication the caller
     * @return the resumed subscription
     */
    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume a canceled subscription")
    @PreAuthorize("@SubscriptionPolicy.canChangeRenewal(authentication, #id)")
    public ResponseEntity<SubscriptionResponse> resume(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.resume(id, SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Activate a subscription waiting for payment, until payments do it.
     *
     * @param id the ID of the subscription
     * @param authentication the caller
     * @return the active subscription
     */
    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a pending subscription")
    @PreAuthorize("@SubscriptionPolicy.canManage(authentication)")
    public ResponseEntity<SubscriptionResponse> activate(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.activate(id, SecurityUtils.extractUserId(authentication)));
    }

    /**
     * Grant a user or a group a license for free.
     *
     * @param request the user, the license and the number of periods
     * @param authentication the caller
     * @return the active subscription
     */
    @PostMapping("/grant")
    @Operation(summary = "Grant a license")
    @PreAuthorize("@SubscriptionPolicy.canManage(authentication)")
    public ResponseEntity<SubscriptionResponse> grant(@Valid @RequestBody GrantSubscriptionRequest request,
                                                      Authentication authentication) {
        return new ResponseEntity<>(subscriptionService.grant(SecurityUtils.extractUserId(authentication), request),
                HttpStatus.CREATED);
    }

    /**
     * Push the end of an active subscription's current period forward.
     *
     * @param id the ID of the subscription
     * @param request how many days to add
     * @param authentication the caller
     * @return the extended subscription
     */
    @PostMapping("/{id}/extend")
    @Operation(summary = "Extend a subscription")
    @PreAuthorize("@SubscriptionPolicy.canManage(authentication)")
    public ResponseEntity<SubscriptionResponse> extend(@PathVariable UUID id,
                                                       @Valid @RequestBody ExtendSubscriptionRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.extend(id, request.getDays(),
                SecurityUtils.extractUserId(authentication)));
    }
}
