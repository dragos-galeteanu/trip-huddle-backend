package com.triphuddle.common.dto;

import java.util.List;
import java.util.function.Function;

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

    /**
     * Build a page from rows fetched with one more than the limit, which tells whether another page follows.
     *
     * @param fetched up to {@code limit + 1} rows, in the list's order
     * @param limit the page size
     * @param cursorOf the cursor of a row: its sort keys, its ID last
     * @param mapper maps the page's rows to response items
     * @return the page
     */
    public static <E, T> CursorPaginated<T> of(List<E> fetched, int limit, Function<E, PageCursor> cursorOf,
                                               Function<List<E>, List<T>> mapper) {
        boolean hasMore = fetched.size() > limit;
        List<E> rows = hasMore ? fetched.subList(0, limit) : fetched;
        String nextCursor = hasMore ? cursorOf.apply(rows.get(rows.size() - 1)).encode() : null;
        return new CursorPaginated<>(mapper.apply(rows), nextCursor, hasMore);
    }
}
