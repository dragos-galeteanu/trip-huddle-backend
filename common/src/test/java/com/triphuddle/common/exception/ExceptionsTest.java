package com.triphuddle.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionsTest {

    @Test
    void eachCanBeRaisedWithOrWithoutAMessage() {
        assertThat(new ResourceNotFoundException()).hasMessage(null);
        assertThat(new InvalidFilterException()).hasMessage(null);
        assertThat(new InsufficientTokensException()).hasMessage(null);
        assertThat(new InsufficientTokensException("none left")).hasMessage("none left");
    }
}
