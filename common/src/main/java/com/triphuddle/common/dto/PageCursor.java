package com.triphuddle.common.dto;

import com.triphuddle.common.exception.InvalidFilterException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Where a cursor page ended: the sort keys of its last item, the item's ID last, sent to clients as opaque text.
 *
 * @param parts the keys, in the list's sort order
 */
public record PageCursor(List<String> parts) {

    /**
     * Stands in for the keys on the first page, which has no cursor, so one query serves every page.
     */
    public static final Instant FIRST_INSTANT = Instant.EPOCH;
    public static final UUID FIRST_ID = new UUID(0, 0);

    /**
     * A cursor from an item's sort keys.
     *
     * @param keys e.g. its creation time then its ID
     * @return the cursor
     */
    public static PageCursor of(Object... keys) {
        return new PageCursor(Arrays.stream(keys).map(String::valueOf).toList());
    }

    /**
     * Read a cursor a client sent back.
     *
     * @param cursor the text, or null or blank for the first page
     * @param size how many keys the list's cursors carry
     * @return the cursor, or null for the first page
     * @throws InvalidFilterException if it isn't one of this list's cursors
     */
    public static PageCursor decode(String cursor, int size) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            List<String> parts = List.of(raw.split("\\|", -1));
            if (parts.size() != size) {
                throw new InvalidFilterException("Invalid cursor");
            }
            return new PageCursor(parts);
        } catch (IllegalArgumentException e) {
            throw new InvalidFilterException("Invalid cursor");
        }
    }

    /**
     * The text sent to clients.
     *
     * @return the cursor, URL-safe
     */
    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A key read as a time.
     *
     * @param index its position
     * @return the time
     */
    public Instant instantAt(int index) {
        try {
            return Instant.parse(parts.get(index));
        } catch (DateTimeParseException e) {
            throw new InvalidFilterException("Invalid cursor");
        }
    }

    /**
     * A key read as an ID.
     *
     * @param index its position
     * @return the ID
     */
    public UUID uuidAt(int index) {
        try {
            return UUID.fromString(parts.get(index));
        } catch (IllegalArgumentException e) {
            throw new InvalidFilterException("Invalid cursor");
        }
    }

    /**
     * A key read as a number.
     *
     * @param index its position
     * @return the number
     */
    public int intAt(int index) {
        try {
            return Integer.parseInt(parts.get(index));
        } catch (NumberFormatException e) {
            throw new InvalidFilterException("Invalid cursor");
        }
    }
}
