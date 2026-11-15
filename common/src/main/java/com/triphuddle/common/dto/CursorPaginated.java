package com.triphuddle.common.dto;

import java.util.List;

/**
 * A page of a cursor-paginated list; pass {@code nextCursor} back as the cursor to get the next page.
 * Prefer it to {@link Paginated} for lists that grow at their head while being read, such as chats.
 *
 * @param data the items of the page
 * @param nextCursor the cursor of the next page, or null if this is the last one
 * @param hasMore whether there is a next page
 * @param <T> the type of the items
 */
public record CursorPaginated<T>(
        List<T> data,
        String nextCursor,
        boolean hasMore
) {
}
