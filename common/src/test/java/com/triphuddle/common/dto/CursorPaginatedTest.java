package com.triphuddle.common.dto;

import com.triphuddle.common.filter.CursorPaginationFilter;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class CursorPaginatedTest {

    private static final Function<Integer, PageCursor> CURSOR = PageCursor::of;
    private static final Function<List<Integer>, List<String>> MAPPER = rows -> rows.stream().map(String::valueOf).toList();

    @Test
    void anExtraRowMeansMoreAndTheCursorPointsAtTheLastRowShown() {
        CursorPaginated<String> page = CursorPaginated.of(List.of(1, 2, 3), 2, CURSOR, MAPPER);

        assertThat(page.data()).containsExactly("1", "2");
        assertThat(page.hasMore()).isTrue();
        assertThat(PageCursor.decode(page.nextCursor(), 1).intAt(0)).isEqualTo(2);
    }

    @Test
    void noExtraRowMeansTheLastPage() {
        CursorPaginated<String> page = CursorPaginated.of(List.of(1, 2), 2, CURSOR, MAPPER);

        assertThat(page.data()).containsExactly("1", "2");
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void anEmptyPageHasNoCursor() {
        CursorPaginated<String> page = CursorPaginated.of(List.of(), 5, CURSOR, MAPPER);

        assertThat(page.data()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void theFilterAsksForOneRowMoreThanItShows() {
        CursorPaginationFilter filter = new CursorPaginationFilter();

        assertThat(filter.getLimit()).isEqualTo(20);
        assertThat(filter.toLimit().getPageSize()).isEqualTo(21);
        assertThat(filter.toLimit().getPageNumber()).isZero();
    }

    @ParameterizedTest(name = "limit {0}: {1} violation(s)")
    @CsvSource({"1, 0", "100, 0", "0, 1", "101, 1"})
    void theLimitRunsFromOneToAHundred(int limit, int violations) {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new CursorPaginationFilter(null, limit))).hasSize(violations);
    }
}
