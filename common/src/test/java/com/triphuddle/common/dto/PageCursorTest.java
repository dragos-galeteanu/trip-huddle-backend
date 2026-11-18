package com.triphuddle.common.dto;

import com.triphuddle.common.exception.InvalidFilterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageCursorTest {

    @Test
    void survivesTheRoundTripWithEveryKeyType() {
        Instant at = Instant.parse("2026-11-16T10:15:30.123Z");
        UUID id = UUID.randomUUID();

        PageCursor decoded = PageCursor.decode(PageCursor.of(at, id, 42).encode(), 3);

        assertThat(decoded.instantAt(0)).isEqualTo(at);
        assertThat(decoded.uuidAt(1)).isEqualTo(id);
        assertThat(decoded.intAt(2)).isEqualTo(42);
    }

    @Test
    void isUrlSafeWithoutPadding() {
        String encoded = PageCursor.of("a?b/c", "~~~").encode();

        assertThat(encoded).doesNotContain("=", "+", "/");
    }

    @Test
    void keepsEmptyKeys() {
        assertThat(PageCursor.decode(PageCursor.of("", "x").encode(), 2).parts()).containsExactly("", "x");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void meansTheFirstPageWhenAbsent(String cursor) {
        assertThat(PageCursor.decode(cursor, 2)).isNull();
    }

    @Test
    void refusesACursorWithTheWrongNumberOfKeys() {
        String twoKeys = PageCursor.of("a", "b").encode();

        assertThatThrownBy(() -> PageCursor.decode(twoKeys, 3)).isInstanceOf(InvalidFilterException.class);
    }

    @Test
    void refusesACursorThatIsNotBase64() {
        assertThatThrownBy(() -> PageCursor.decode("not base64!", 1)).isInstanceOf(InvalidFilterException.class);
    }

    @Test
    void refusesKeysOfTheWrongType() {
        PageCursor cursor = new PageCursor(List.of("yesterday", "not-a-uuid", "ten"));

        assertThatThrownBy(() -> cursor.instantAt(0)).isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> cursor.uuidAt(1)).isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> cursor.intAt(2)).isInstanceOf(InvalidFilterException.class);
    }

    @Test
    void decodesWhatAnotherInstanceEncoded() {
        String handMade = Base64.getUrlEncoder().withoutPadding().encodeToString("1|2".getBytes());

        assertThat(PageCursor.decode(handMade, 2).intAt(1)).isEqualTo(2);
    }
}
