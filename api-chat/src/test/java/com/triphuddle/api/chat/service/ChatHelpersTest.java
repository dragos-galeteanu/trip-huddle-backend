package com.triphuddle.api.chat.service;

import com.triphuddle.api.chat.config.ChatProperties;
import com.triphuddle.api.chat.config.SupportProperties;
import com.triphuddle.api.chat.entity.ChatMessage;
import com.triphuddle.api.chat.entity.MessageAttachment;
import com.triphuddle.api.chat.job.SupportTicketAutoCloseJob;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatHelpersTest {

    @Nested
    class Previews {

        ChatMessage message(String body, List<MessageAttachment> attachments) {
            ChatMessage message = new ChatMessage();
            message.setBody(body);
            message.setAttachments(attachments);
            return message;
        }

        MessageAttachment file(String name) {
            return new MessageAttachment("p", name, "image/png", 1L);
        }

        @Test
        void a_preview_is_the_trimmed_body_cut_at_100_characters() {
            assertThat(ConversationService.preview(message("  Hi  ", List.of()))).isEqualTo("Hi");
            assertThat(ConversationService.preview(message("x".repeat(150), List.of())))
                    .isEqualTo("x".repeat(100) + "…");
        }

        @Test
        void without_a_body_the_preview_names_the_attachments() {
            assertThat(ConversationService.preview(message(null, List.of(file("a.png"))))).isEqualTo("📎 a.png");
            assertThat(ConversationService.preview(message(" ", List.of(file("y".repeat(120))))))
                    .isEqualTo("📎 " + "y".repeat(100) + "…");
            assertThat(ConversationService.preview(message(null, List.of(file("a"), file("b")))))
                    .isEqualTo("📎 2 attachments");
            assertThat(ConversationService.preview(message(null, null))).isNull();
            assertThat(ConversationService.preview(message(null, List.of()))).isNull();
        }

        @Test
        void a_deleted_message_has_no_preview() {
            ChatMessage message = message("Gone", List.of());
            message.setDeletedAt(Instant.now());

            assertThat(ConversationService.preview(message)).isNull();
        }
    }

    @Nested
    class Auto_close_job {

        final SupportTicketService tickets = mock(SupportTicketService.class);
        final SupportTicketAutoCloseJob job = new SupportTicketAutoCloseJob(tickets,
                new ChatProperties(null, null, new SupportProperties(3, 7, 1000)));

        @Test
        void it_keeps_closing_full_batches_until_one_comes_back_short() {
            given(tickets.autoCloseResolved(any(), anyInt())).willReturn(50, 50, 3);

            job.closeResolvedTickets();

            then(tickets).should(times(3)).autoCloseResolved(any(), anyInt());
        }

        @Test
        void it_stops_after_twenty_batches_or_at_the_first_failure() {
            given(tickets.autoCloseResolved(any(), anyInt())).willReturn(50);
            job.closeResolvedTickets();
            then(tickets).should(times(20)).autoCloseResolved(any(), anyInt());

            SupportTicketService failing = mock(SupportTicketService.class);
            given(failing.autoCloseResolved(any(), anyInt())).willThrow(new IllegalStateException("db down"));
            new SupportTicketAutoCloseJob(failing, new ChatProperties(null, null, new SupportProperties(3, 7, 1000)))
                    .closeResolvedTickets();
            then(failing).should(times(1)).autoCloseResolved(any(), anyInt());
        }

        @Test
        void a_run_with_nothing_due_closes_nothing() {
            given(tickets.autoCloseResolved(any(), anyInt())).willReturn(0);

            job.closeResolvedTickets();

            then(tickets).should(times(1)).autoCloseResolved(any(), anyInt());
        }
    }
}
