package com.triphuddle.api.platform.fixtures;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Removes what tests created, leaving the seeded data alone.
 */
public final class PlatformTestData {

    private PlatformTestData() {
    }

    /**
     * Hard-delete the rows created from the fixtures' code ranges, countries first for their foreign keys.
     */
    public static void deleteTestRows(JdbcTemplate jdbc) {
        jdbc.update("delete from countries where numeric_code >= '900'");
        jdbc.update("delete from currencies where code like 'Q%' and code <> 'QAR'");
        jdbc.update("delete from languages where code like 'zz%'");
    }
}
