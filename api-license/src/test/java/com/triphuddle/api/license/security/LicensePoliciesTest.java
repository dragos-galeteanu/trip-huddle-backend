package com.triphuddle.api.license.security;

import com.triphuddle.amqp.license.LicenseTarget;
import com.triphuddle.api.license.dto.CreateLicenseRequest;
import com.triphuddle.api.license.dto.SubscribeRequest;
import com.triphuddle.api.license.dto.UpdateLicenseRequest;
import com.triphuddle.api.license.entity.LicenseStatus;
import com.triphuddle.api.license.entity.Subscription;
import com.triphuddle.api.license.entity.SubscriptionStatus;
import com.triphuddle.api.license.fixtures.LicenseFixtures;
import com.triphuddle.api.license.repository.GroupAdminRepository;
import com.triphuddle.api.license.repository.SubscriptionRepository;
import com.triphuddle.testsupport.security.AbstractAuthorizationPolicyTest;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static com.triphuddle.testsupport.security.Caller.ADMIN;
import static com.triphuddle.testsupport.security.Caller.CUSTOMER;
import static com.triphuddle.testsupport.security.Caller.INSPECTOR;
import static com.triphuddle.testsupport.security.Caller.SALES;
import static com.triphuddle.testsupport.security.Caller.SUPPORT;
import static com.triphuddle.testsupport.security.Operation.CREATE;
import static com.triphuddle.testsupport.security.Operation.DELETE;
import static com.triphuddle.testsupport.security.Operation.GET_ALL;
import static com.triphuddle.testsupport.security.Operation.GET_BY_ID;
import static com.triphuddle.testsupport.security.Operation.UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class LicensePoliciesTest {

    /** Anyone reads the catalogue (the service hides drafts); staff manage it; the lifecycle replaces disabling. */
    @Nested
    class Licenses extends AbstractAuthorizationPolicyTest<CreateLicenseRequest, UpdateLicenseRequest,
            LicenseAuthorizationPolicy> {

        @Override
        protected LicenseAuthorizationPolicy policy() {
            return new LicenseAuthorizationPolicy();
        }

        @Override
        protected AuthorizationMatrix matrix() {
            return AuthorizationMatrix.create()
                    .allow(CREATE, ADMIN, SUPPORT)
                    .allowEveryone(GET_BY_ID)
                    .allowEveryone(GET_ALL)
                    .allow(UPDATE, ADMIN, SUPPORT)
                    .allow(DELETE, ADMIN, SUPPORT);
        }

        @Override
        protected CreateLicenseRequest createRequest() {
            return LicenseFixtures.createLicense(LicenseTarget.USER, 999);
        }

        @Override
        protected UpdateLicenseRequest updateRequest() {
            return new UpdateLicenseRequest();
        }

        @Test
        void staffManageAndInspectorsAlsoReadPromotions() {
            LicenseAuthorizationPolicy policy = policy();

            assertThat(policy.canManage(SUPPORT.authentication())).isTrue();
            assertThat(policy.canManage(INSPECTOR.authentication())).isFalse();
            assertThat(policy.canReadPromotions(INSPECTOR.authentication())).isTrue();
            assertThat(policy.canReadPromotions(SALES.authentication())).isFalse();
        }
    }

    /** LIC-5: users subscribe themselves, only a group's admins subscribe it; staff read and manage everything. */
    @Nested
    class Subscriptions extends AbstractAuthorizationPolicyTest<SubscribeRequest, Void,
            SubscriptionAuthorizationPolicy> {

        private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        private final GroupAdminRepository groupAdmins = mock(GroupAdminRepository.class);

        @Override
        protected SubscriptionAuthorizationPolicy policy() {
            return new SubscriptionAuthorizationPolicy(subscriptions, groupAdmins);
        }

        @Override
        protected AuthorizationMatrix matrix() {
            return AuthorizationMatrix.create()
                    .allow(CREATE, CUSTOMER, SALES, INSPECTOR, SUPPORT, ADMIN)
                    .allow(GET_BY_ID, ADMIN, SUPPORT, INSPECTOR)
                    .allow(GET_ALL, ADMIN, SUPPORT, INSPECTOR);
        }

        @Override
        protected SubscribeRequest createRequest() {
            return SubscribeRequest.builder().licenseId(UUID.randomUUID()).build();
        }

        @Override
        protected Void updateRequest() {
            return null;
        }

        @Test
        void aUserReadsAndChangesTheRenewalOfTheirOwnSubscription() {
            Subscription mine = givenSubscription(LicenseTarget.USER);
            Authentication me = CUSTOMER.authentication(mine.getSubscriberId());

            assertThat(policy().canGetById(me, mine.getId())).isTrue();
            assertThat(policy().canChangeRenewal(me, mine.getId())).isTrue();
            assertThat(policy().canChangeRenewal(CUSTOMER.authentication(), mine.getId())).isFalse();
            assertThat(policy().canGetMine(me)).isTrue();
        }

        @Test
        void onlyAGroupsAdminsSubscribeItReadItsSubscriptionsAndChangeTheirRenewal() {
            Subscription ofGroup = givenSubscription(LicenseTarget.GROUP);
            UUID admin = UUID.randomUUID();
            given(groupAdmins.existsByGroupIdAndUserId(ofGroup.getSubscriberId(), admin)).willReturn(true);
            Authentication asAdmin = CUSTOMER.authentication(admin);
            SubscribeRequest forGroup = SubscribeRequest.builder().licenseId(UUID.randomUUID())
                    .groupId(ofGroup.getSubscriberId()).build();

            assertThat(policy().canCreate(asAdmin, forGroup)).isTrue();
            assertThat(policy().canCreate(CUSTOMER.authentication(), forGroup)).isFalse();
            assertThat(policy().canGetForGroup(asAdmin, ofGroup.getSubscriberId())).isTrue();
            assertThat(policy().canGetById(asAdmin, ofGroup.getId())).isTrue();
            assertThat(policy().canChangeRenewal(asAdmin, ofGroup.getId())).isTrue();
            assertThat(policy().canGetForGroup(CUSTOMER.authentication(), ofGroup.getSubscriberId())).isFalse();
            assertThat(policy().isGroupAdmin(Caller.ANONYMOUS.authentication(), ofGroup.getSubscriberId())).isFalse();
            assertThat(policy().isGroupAdmin(asAdmin, null)).isFalse();
        }

        @Test
        void staffChangeAnyRenewalAndManageButInspectorsOnlyRead() {
            assertThat(policy().canChangeRenewal(SUPPORT.authentication(), UUID.randomUUID())).isTrue();
            assertThat(policy().canManage(ADMIN.authentication())).isTrue();
            assertThat(policy().canManage(INSPECTOR.authentication())).isFalse();
            assertThat(policy().canGetForGroup(INSPECTOR.authentication(), UUID.randomUUID())).isTrue();
        }

        private Subscription givenSubscription(LicenseTarget target) {
            Subscription subscription = LicenseFixtures.subscription(
                    LicenseFixtures.license(target, LicenseStatus.PUBLISHED, 999), SubscriptionStatus.ACTIVE);
            given(subscriptions.findByIdAndDeletedAtIsNull(subscription.getId())).willReturn(Optional.of(subscription));
            return subscription;
        }
    }
}
