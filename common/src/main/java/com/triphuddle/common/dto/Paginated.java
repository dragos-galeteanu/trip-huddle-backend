package com.triphuddle.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Paginated response DTO.
 * @param data list of items
 * @param metaData pagination metadata
 * @param <T> type of the items
 */
public record Paginated<T>(
        List<T> data,
        PaginationMetaData metaData
) {

    /**
     * Build a paginated response from a page of entities.
     *
     * @param page the page returned by a repository
     * @param mapper maps the entities of the page to response items
     * @return the paginated response, with 1-indexed page metadata
     */
    public static <E, T> Paginated<T> of(Page<E> page, Function<List<E>, List<T>> mapper) {
        PaginationMetaData paginationMetaData = new PaginationMetaData(
                page.getTotalElements(),
                page.getSize(),
                page.getNumber() + 1,
                page.getTotalPages()
        );

        return new Paginated<>(mapper.apply(page.getContent()), paginationMetaData);
    }
}
