package com.triphuddle.api.payment.service;

import com.triphuddle.amqp.user.UserContactUpdatedEvent;
import com.triphuddle.api.payment.entity.UserContact;
import com.triphuddle.api.payment.repository.UserContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Keeps the local copy of user contacts in step with api-user's events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserContactService {

    private final UserContactRepository userContactRepository;
    private final CustomerService customerService;

    /**
     * Apply a user's contact, unless a newer one is already stored.
     *
     * @param event the contact
     */
    public void onContactUpdated(UserContactUpdatedEvent event) {
        UserContact contact = findOrNew(event.userId());
        if (contact.getContactChangedAt() != null && !event.changedAt().isAfter(contact.getContactChangedAt())) {
            log.info("Stale user contact skipped [userId={}, changedAt={}]", event.userId(), event.changedAt());
            return;
        }
        contact.setEmail(event.email());
        contact.setFirstName(event.firstName());
        contact.setLastName(event.lastName());
        contact.setEmailVerified(event.isEmailVerified());
        contact.setContactChangedAt(event.changedAt());
        userContactRepository.save(contact);
        customerService.onUserContactChanged(contact);
        log.info("User contact recorded [userId={}]", event.userId());
    }

    /**
     * Mark a user disabled.
     *
     * @param userId the ID of the user
     */
    public void onUserDisabled(UUID userId) {
        UserContact contact = findOrNew(userId);
        if (contact.getDisabledAt() == null) {
            contact.setDisabledAt(Instant.now());
            userContactRepository.save(contact);
        }
        log.info("User contact disabled [userId={}]", userId);
    }

    /**
     * Mark a user deleted.
     *
     * @param userId the ID of the user
     */
    public void onUserDeleted(UUID userId) {
        UserContact contact = findOrNew(userId);
        if (contact.getDeletedAt() == null) {
            contact.setDeletedAt(Instant.now());
            userContactRepository.save(contact);
        }
        log.info("User contact deleted [userId={}]", userId);
    }

    private UserContact findOrNew(UUID userId) {
        return userContactRepository.findByUserId(userId).orElseGet(() -> {
            UserContact contact = new UserContact();
            contact.setUserId(userId);
            return contact;
        });
    }
}
