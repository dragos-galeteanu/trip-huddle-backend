package com.triphuddle.api.chat.config;

import com.triphuddle.api.chat.security.ChatAccess;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatStompAuthChannelInterceptorTest {

    final ChatAccess access = mock(ChatAccess.class);
    final ChatStompAuthChannelInterceptor interceptor =
            new ChatStompAuthChannelInterceptor(mock(JwtDecoder.class), jwt -> null, access);

    @Test
    void only_support_staff_subscribe_to_the_support_queue() {
        Authentication support = Caller.SUPPORT.authentication();
        Authentication customer = Caller.CUSTOMER.authentication();
        given(access.isSupportStaff(support)).willReturn(true);

        assertThat(interceptor.maySubscribe(support, UUID.randomUUID(),
                ChatStompAuthChannelInterceptor.SUPPORT_QUEUE_TOPIC)).isTrue();
        assertThat(interceptor.maySubscribe(customer, UUID.randomUUID(),
                ChatStompAuthChannelInterceptor.SUPPORT_QUEUE_TOPIC)).isFalse();
    }

    @Test
    void any_other_destination_follows_the_shared_rules() {
        UUID me = UUID.randomUUID();
        Authentication customer = Caller.CUSTOMER.authentication(me);

        assertThat(interceptor.maySubscribe(customer, me, com.triphuddle.common.config.StompDestinations.userTopic(me)))
                .isTrue();
        assertThat(interceptor.maySubscribe(customer, me,
                com.triphuddle.common.config.StompDestinations.userTopic(UUID.randomUUID()))).isFalse();
    }
}
