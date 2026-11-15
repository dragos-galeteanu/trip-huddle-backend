package com.triphuddle.api.payment.security;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.payment.entity.PaymentInvoice;
import com.triphuddle.api.payment.repository.GroupAdminRepository;
import com.triphuddle.api.payment.repository.PaymentInvoiceRepository;
import com.triphuddle.common.security.BaseAuthorizationPolicy;
import com.triphuddle.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A user reads their own invoices, a group's admins the group's, and Admin, Support and Inspector all of them;
 * only Admin and Support refund.
 */
@Component("InvoicePolicy")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceAuthorizationPolicy implements BaseAuthorizationPolicy<Void, Void, UUID> {

    private final PaymentInvoiceRepository paymentInvoiceRepository;
    private final GroupAdminRepository groupAdminRepository;

    @Override
    public boolean canCreate(Authentication authentication, Void request) {
        // Invoices come only from Stripe, through the webhook.
        return false;
    }

    @Override
    public boolean canGetById(Authentication authentication, UUID id) {
        if (isAdminOrSupportOrInspector(authentication)) {
            return true;
        }
        if (!isAuthenticated(authentication) || id == null) {
            return false;
        }
        return paymentInvoiceRepository.findByIdAndDeletedAtIsNull(id)
                .map(invoice -> isSubscriberOrItsAdmin(authentication, invoice))
                .orElse(false);
    }

    @Override
    public boolean canGetAll(Authentication authentication) {
        return isAdminOrSupportOrInspector(authentication);
    }

    /**
     * Check if the user can list their own invoices.
     *
     * @param authentication the authentication object
     * @return true if the user is signed in
     */
    public boolean canGetMine(Authentication authentication) {
        return isAuthenticated(authentication);
    }

    /**
     * Check if the user can list a group's invoices.
     *
     * @param authentication the authentication object
     * @param groupId the ID of the group
     * @return true if the user is Admin, Support, Inspector or an admin of the group
     */
    public boolean canGetForGroup(Authentication authentication, UUID groupId) {
        return isAdminOrSupportOrInspector(authentication) || isGroupAdmin(authentication, groupId);
    }

    /**
     * Check if the user can refund invoices.
     *
     * @param authentication the authentication object
     * @return true if the user is Admin or Support
     */
    public boolean canRefund(Authentication authentication) {
        return isAdminOrSupport(authentication);
    }

    private boolean isSubscriberOrItsAdmin(Authentication authentication, PaymentInvoice invoice) {
        return invoice.getSubscriberType() == LicenseTarget.USER
                ? isCurrentUser(authentication, invoice.getSubscriberId())
                : isGroupAdmin(authentication, invoice.getSubscriberId());
    }

    private boolean isGroupAdmin(Authentication authentication, UUID groupId) {
        return isAuthenticated(authentication) && groupId != null
                && groupAdminRepository.existsByGroupIdAndUserId(groupId, SecurityUtils.extractUserId(authentication));
    }

    @Override
    public boolean canUpdate(Authentication authentication, Void request) {
        return false;
    }

    @Override
    public boolean canDelete(Authentication authentication, UUID id) {
        return false;
    }

    @Override
    public boolean canDisable(Authentication authentication) {
        return false;
    }
}
