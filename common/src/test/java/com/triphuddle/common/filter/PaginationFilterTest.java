package com.triphuddle.common.filter;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationFilterTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void turnsTheOneBasedPageIntoAZeroBasedPageable() {
        Pageable pageable = new PaginationFilter(3, 20).toPageable();

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @ParameterizedTest(name = "page {0}, size {1}: {2} violation(s)")
    @CsvSource({"1, 1, 0", "1, 100, 0", "0, 10, 1", "1, 0, 1", "1, 101, 1", "0, 0, 2"})
    void acceptsPagesFromOneAndSizesFromOneToAHundred(int page, int size, int violations) {
        assertThat(validator.validate(new PaginationFilter(page, size))).hasSize(violations);
    }
}
