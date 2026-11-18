package com.triphuddle.api.chat.security;

import com.triphuddle.api.chat.dto.CreateSupportTicketRequest;
import com.triphuddle.api.chat.entity.SupportTicket;
import com.triphuddle.api.chat.repository.SupportTicketRepository;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** Who may do what in api-chat; the participant checks themselves are ChatAccess's, tested on their own. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatPoliciesTest {

    final ChatAccess access = mock(ChatAccess.class);
    final UUID me = UUID.randomUUID();
    final Authentication customer = Caller.CUSTOMER.authentication(me);
    final Authentication anonymous = Caller.ANONYMOUS.authentication();

    @Nested
    class Conversations {
        final ConversationAuthorizationPolicy policy = new ConversationAuthorizationPolicy(access);

        @Test
        void signed_in_users_open_and_list_chats_but_nobody_edits_deletes_or_disables_them() {
            UUID id = UUID.randomUUID();
            given(access.isParticipant(customer, id)).willReturn(true);

            assertThat(policy.canCreate(customer, null)).isTrue();
            assertThat(policy.canCreate(anonymous, null)).isFalse();
            assertThat(policy.canGetAll(customer)).isTrue();
            assertThat(policy.canGetById(customer, id)).isTrue();
            assertThat(policy.canGetById(customer, UUID.randomUUID())).isFalse();
            assertThat(policy.canUpdate(customer, null)).isFalse();
            assertThat(policy.canDelete(Caller.ADMIN.authentication(), id)).isFalse();
            assertThat(policy.canDisable(Caller.ADMIN.authentication())).isFalse();
        }
    }

    @Nested
    class Messages {
        final ChatMessageAuthorizationPolicy policy = new ChatMessageAuthorizationPolicy(access);

        @Test
        void the_sender_edits_and_deletes_while_still_in_the_chat() {
            given(access.findSenderId("m")).willReturn(Optional.of(me));
            given(access.isActiveInMessageConversation(customer, "m")).willReturn(true);

            assertThat(policy.canEdit(customer, "m")).isTrue();
            assertThat(policy.canDelete(customer, "m")).isTrue();
            assertThat(policy.canEdit(Caller.CUSTOMER.authentication(), "m")).isFalse();
        }

        @Test
        void a_group_admin_deletes_anyones_message_but_never_edits_it() {
            Authentication admin = Caller.CUSTOMER.authentication();
            given(access.findSenderId("m")).willReturn(Optional.of(me));
            given(access.isGroupAdminOfMessageConversation(admin, "m")).willReturn(true);

            assertThat(policy.canDelete(admin, "m")).isTrue();
            assertThat(policy.canEdit(admin, "m")).isFalse();
        }

        @Test
        void sending_reading_and_reacting_follow_the_chat() {
            UUID chat = UUID.randomUUID();
            given(access.isActiveParticipant(customer, chat)).willReturn(true);
            given(access.canReadHistory(customer, chat)).willReturn(true);
            given(access.canReadMessage(customer, "m")).willReturn(true);
            given(access.isActiveInMessageConversation(customer, "m")).willReturn(true);

            assertThat(policy.canSend(customer, chat)).isTrue();
            assertThat(policy.canGetAllByConversation(customer, chat)).isTrue();
            assertThat(policy.canGetById(customer, "m")).isTrue();
            assertThat(policy.canReact(customer, "m")).isTrue();
            assertThat(policy.canCreate(customer, null)).isFalse();
            assertThat(policy.canUpdate(customer, null)).isFalse();
            assertThat(policy.canGetAll(customer)).isFalse();
            assertThat(policy.canDisable(customer)).isFalse();
        }
    }

    @Nested
    class Tickets {
        final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
        final SupportTicketAuthorizationPolicy policy = new SupportTicketAuthorizationPolicy(tickets);
        final UUID id = UUID.randomUUID();

        SupportTicket ticket(UUID requester, UUID assignee) {
            SupportTicket ticket = new SupportTicket();
            ticket.setRequesterId(requester);
            ticket.setAssigneeId(assignee);
            given(tickets.findByIdAndDeletedAtIsNull(id)).willReturn(Optional.of(ticket));
            return ticket;
        }

        @Test
        void customers_open_their_own_tickets_and_only_staff_open_one_towards_someone() {
            CreateSupportTicketRequest own = CreateSupportTicketRequest.builder().build();
            CreateSupportTicketRequest outbound = own.toBuilder().userId(UUID.randomUUID()).build();

            assertThat(policy.canCreate(customer, own)).isTrue();
            assertThat(policy.canCreate(anonymous, own)).isFalse();
            assertThat(policy.canCreate(customer, outbound)).isFalse();
            assertThat(policy.canCreate(Caller.SUPPORT.authentication(), outbound)).isTrue();
        }

        @Test
        void the_requester_reads_rates_and_moves_their_ticket_and_staff_handle_all() {
            ticket(me, null);
            Authentication support = Caller.SUPPORT.authentication();

            assertThat(policy.canGetById(customer, id)).isTrue();
            assertThat(policy.canChangeStatus(customer, id)).isTrue();
            assertThat(policy.canRate(customer, id)).isTrue();
            assertThat(policy.canRate(support, id)).isFalse();
            assertThat(policy.canGetById(Caller.CUSTOMER.authentication(), id)).isFalse();
            assertThat(policy.canGetById(support, id)).isTrue();
            assertThat(policy.canGetAll(support)).isTrue();
            assertThat(policy.canGetAll(customer)).isFalse();
            assertThat(policy.canGetMine(customer)).isTrue();
            assertThat(policy.canUpdate(support, null)).isTrue();
            assertThat(policy.canHandle(customer)).isFalse();
            assertThat(policy.canDelete(support, id)).isFalse();
            assertThat(policy.canDisable(support)).isFalse();
        }

        @Test
        void the_assignee_or_an_admin_releases_a_ticket() {
            UUID agent = UUID.randomUUID();
            ticket(me, agent);

            assertThat(policy.canRelease(Caller.SUPPORT.authentication(agent), id)).isTrue();
            assertThat(policy.canRelease(Caller.SUPPORT.authentication(), id)).isFalse();
            assertThat(policy.canRelease(Caller.ADMIN.authentication(), id)).isTrue();
            assertThat(policy.canRelease(customer, id)).isFalse();
            given(tickets.findByIdAndDeletedAtIsNull(any())).willReturn(Optional.empty());
            assertThat(policy.canRelease(Caller.SUPPORT.authentication(agent), id)).isFalse();
        }
    }

    @Nested
    class Reports_and_blocks {

        @Test
        void readers_report_and_moderators_review() {
            MessageReportAuthorizationPolicy policy = new MessageReportAuthorizationPolicy(access);
            given(access.canReadMessage(customer, "m")).willReturn(true);

            assertThat(policy.canReport(customer, "m")).isTrue();
            assertThat(policy.canCreate(customer, null)).isFalse();
            for (Caller staff : new Caller[]{Caller.ADMIN, Caller.SUPPORT, Caller.INSPECTOR}) {
                Authentication authentication = staff.authentication();
                assertThat(policy.canGetAll(authentication)).isTrue();
                assertThat(policy.canGetById(authentication, UUID.randomUUID())).isTrue();
                assertThat(policy.canUpdate(authentication, null)).isTrue();
            }
            assertThat(policy.canGetAll(customer)).isFalse();
            assertThat(policy.canGetAll(Caller.SALES.authentication())).isFalse();
            assertThat(policy.canDelete(Caller.ADMIN.authentication(), UUID.randomUUID())).isFalse();
        }

        @Test
        void signed_in_users_manage_only_their_own_blocks() {
            UserBlockAuthorizationPolicy policy = new UserBlockAuthorizationPolicy();

            assertThat(policy.canCreate(customer, null)).isTrue();
            assertThat(policy.canGetAll(customer)).isTrue();
            assertThat(policy.canDelete(customer, UUID.randomUUID())).isTrue();
            assertThat(policy.canCreate(anonymous, null)).isFalse();
            assertThat(policy.canGetById(customer, UUID.randomUUID())).isFalse();
            assertThat(policy.canUpdate(customer, null)).isFalse();
            assertThat(policy.canDisable(customer)).isFalse();
        }
    }
}
