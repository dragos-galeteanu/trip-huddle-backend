package com.triphuddle.api.platform.migration;

import com.triphuddle.testsupport.IntegrationTest;
import com.triphuddle.testsupport.containers.MongoContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The data V2 seeds. The schema itself is checked by every integration test: Flyway builds it, then Hibernate
 * validates the entities against it. Rows from the fixtures' code ranges are left out of every count.
 */
@IntegrationTest
@Import(MongoContainerConfig.class)
class SeedMigrationIT {

    private static final String SEEDED_COUNTRIES = "numeric_code < '900'";
    private static final String SEEDED_CURRENCIES = "(code not like 'Q%' or code = 'QAR')";
    private static final String SEEDED_LANGUAGES = "code not like 'zz%'";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Test
    void seedsEveryIsoCountryAndTheCurrenciesAndLanguagesTheyUse() {
        assertThat(count("countries", SEEDED_COUNTRIES)).isEqualTo(249);
        assertThat(count("currencies", SEEDED_CURRENCIES)).isEqualTo(153);
        assertThat(count("languages", SEEDED_LANGUAGES)).isEqualTo(77);
    }

    @Test
    void everyCountryButAntarcticaHasADefaultCurrencyAndLanguage() {
        assertThat(jdbc.queryForList("select alpha2 from countries where " + SEEDED_COUNTRIES
                + " and (default_currency_id is null or default_language_id is null)", String.class))
                .containsExactly("AQ");
    }

    @Test
    void membershipsMatchTheTreatiesIn2026() {
        assertThat(alpha2Where("is_eu_member")).hasSize(27).contains("BG", "HR", "CY").doesNotContain("GB", "CH");
        assertThat(alpha2Where("is_eu_vat_member")).isEqualTo(alpha2Where("is_eu_member"));
        assertThat(alpha2Where("is_eea_member")).hasSize(30).contains("IS", "LI", "NO");
        assertThat(alpha2Where("is_schengen_member")).hasSize(29).contains("CH", "BG", "RO")
                .doesNotContain("IE", "CY");
    }

    @Test
    void bulgariaUsesTheEuroAndCuracaoTheCaribbeanGuilder() {
        assertThat(defaultCurrencyOf("BG")).isEqualTo("EUR");
        assertThat(defaultCurrencyOf("CW")).isEqualTo("XCG");
        assertThat(defaultCurrencyOf("SX")).isEqualTo("XCG");
    }

    @Test
    void vatRatesAreSetForEuropeOnly() {
        assertThat(jdbc.queryForObject("select standard_vat_rate::text from countries where alpha2 = 'FI'",
                String.class)).isEqualTo("25.50");
        assertThat(count("countries", "standard_vat_rate is not null")).isEqualTo(33);
    }

    @Test
    void disablesTheUnservedCountriesAndOnlyTheCurrenciesNoServedCountryUses() {
        assertThat(alpha2Where("disabled_at is not null")).hasSize(59)
                .contains("RU", "BY", "KP", "IR", "PS", "NG", "ZA", "EH")
                .doesNotContain("MA", "EG", "ET", "MG", "CV", "IL");
        assertThat(jdbc.queryForList("select code from currencies where disabled_at is not null", String.class))
                .hasSize(45).contains("XOF", "XAF", "RUB", "ZAR")
                .doesNotContain("MAD", "ILS", "EUR", "USD");
    }

    @Test
    void writesRightToLeftLanguagesAsSuch() {
        assertThat(jdbc.queryForList("select code from languages where is_right_to_left order by code", String.class))
                .containsExactly("ar", "dv", "fa", "he", "ps", "ur");
    }

    @Test
    void runningTheSeedAgainChangesNothing() {
        List<Integer> before = List.of(count("countries", SEEDED_COUNTRIES), count("currencies", SEEDED_CURRENCIES),
                count("languages", SEEDED_LANGUAGES), count("countries", "disabled_at is not null"));

        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__seed_countries_currencies_languages.sql"))
                .execute(dataSource);

        assertThat(List.of(count("countries", SEEDED_COUNTRIES), count("currencies", SEEDED_CURRENCIES),
                count("languages", SEEDED_LANGUAGES), count("countries", "disabled_at is not null")))
                .isEqualTo(before);
    }

    private int count(String table, String where) {
        Integer count = jdbc.queryForObject("select count(*) from " + table + " where " + where, Integer.class);
        return count == null ? 0 : count;
    }

    private List<String> alpha2Where(String where) {
        return jdbc.queryForList("select alpha2 from countries where " + SEEDED_COUNTRIES + " and " + where
                + " order by alpha2", String.class);
    }

    private String defaultCurrencyOf(String alpha2) {
        return jdbc.queryForObject("""
                select cur.code from countries c join currencies cur on cur.id = c.default_currency_id
                where c.alpha2 = ?""", String.class, alpha2);
    }
}
