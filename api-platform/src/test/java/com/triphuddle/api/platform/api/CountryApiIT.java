package com.triphuddle.api.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.platform.CountryCreatedContract;
import com.triphuddle.amqp.platform.CountryCreatedEvent;
import com.triphuddle.amqp.platform.CountryDeletedContract;
import com.triphuddle.amqp.platform.CountryDeletedEvent;
import com.triphuddle.amqp.platform.CountryDisabledContract;
import com.triphuddle.amqp.platform.CountryDisabledEvent;
import com.triphuddle.api.platform.dto.CreateCountryRequest;
import com.triphuddle.api.platform.dto.UpdateCountryRequest;
import com.triphuddle.api.platform.fixtures.CountryFixtures;
import com.triphuddle.api.platform.fixtures.CurrencyFixtures;
import com.triphuddle.api.platform.fixtures.LanguageFixtures;
import com.triphuddle.api.platform.fixtures.PlatformTestData;
import com.triphuddle.api.platform.security.PlatformMatrices;
import com.triphuddle.api.platform.service.CountryService;
import com.triphuddle.testsupport.api.AbstractCrudApiIT;
import com.triphuddle.testsupport.containers.MongoContainerConfig;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(MongoContainerConfig.class)
class CountryApiIT extends AbstractCrudApiIT<CreateCountryRequest, UpdateCountryRequest> {

    private static final String CURRENCIES = "/api/v1/platform/currencies";
    private static final String LANGUAGES = "/api/v1/platform/languages";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CountryService countryService;

    @Autowired
    TransactionTemplate transaction;

    @AfterEach
    void deleteTestRows() {
        PlatformTestData.deleteTestRows(jdbc);
    }

    @Override
    protected String basePath() {
        return "/api/v1/platform/countries";
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return PlatformMatrices.REFERENCE_DATA;
    }

    @Override
    protected CreateCountryRequest validCreate() {
        return CountryFixtures.createRequest().build();
    }

    @Override
    protected UpdateCountryRequest validUpdate(UUID id) {
        return CountryFixtures.updateRequest(id).build();
    }

    @Override
    protected Map<String, CreateCountryRequest> invalidCreates() {
        return Map.of(
                "lower-case alpha-2", CountryFixtures.createRequest().alpha2("qb").build(),
                "three-letter alpha-2", CountryFixtures.createRequest().alpha2("QBB").build(),
                "four-letter alpha-3", CountryFixtures.createRequest().alpha3("QBBB").build(),
                "numeric code with a letter", CountryFixtures.createRequest().numericCode("9A1").build(),
                "blank name", CountryFixtures.createRequest().name(" ").build(),
                "calling code over 8 characters", CountryFixtures.createRequest().callingCode("+123456789").build(),
                "VAT with three decimals", CountryFixtures.createRequest().standardVatRate(new BigDecimal("19.001")).build(),
                "negative VAT", CountryFixtures.createRequest().standardVatRate(new BigDecimal("-1")).build(),
                "no EU membership flag", CountryFixtures.createRequest().isEuMember(null).build());
    }

    @Override
    protected Optional<CreateCountryRequest> clashingCreate(CreateCountryRequest existing) {
        return Optional.of(CountryFixtures.createRequest().alpha2(existing.getAlpha2()).build());
    }

    @Override
    protected void assertUpdated(JsonNode after, UpdateCountryRequest update) {
        assertThat(after.path("name").asText()).isEqualTo(update.getName());
        assertThat(after.path("alpha2").asText()).isEqualTo(update.getAlpha2());
        assertThat(after.path("standardVatRate").decimalValue()).isEqualByComparingTo(update.getStandardVatRate());
        assertThat(after.path("isEuMember").asBoolean()).isTrue();
    }

    // --- PLT-3: events, and only once committed (NFR-4) -----------------------------------------------------------

    @Test
    void publishesCreatedDisabledAndDeleted() throws Exception {
        JsonNode created = create(validCreate());
        UUID id = idOf(created);
        String alpha2 = created.path("alpha2").asText();
        mvc.perform(delete(basePath() + "/" + id + "/disable").with(Caller.ADMIN.request())).andExpect(status().isOk());
        mvc.perform(delete(basePath() + "/" + id).with(Caller.ADMIN.request())).andExpect(status().isOk());

        published.assertPublished(new CountryCreatedEvent(id, alpha2), CountryCreatedContract.COUNTRY_CREATED_QUEUE_NAME);
        published.assertPublished(new CountryDisabledEvent(id, alpha2),
                CountryDisabledContract.COUNTRY_DISABLED_QUEUE_NAME);
        published.assertPublished(new CountryDeletedEvent(id, alpha2), CountryDeletedContract.COUNTRY_DELETED_QUEUE_NAME);
    }

    @Test
    void aCreateThatIsRolledBackPublishesNothing() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            countryService.create(validCreate());
            throw new IllegalStateException("the surrounding work fails after the country was created");
        })).isInstanceOf(IllegalStateException.class);

        published.assertNothingPublished();
    }

    // --- default currency and language ----------------------------------------------------------------------------

    @Test
    void linksItsDefaultCurrencyAndLanguage() throws Exception {
        UUID currencyId = idOf(body(send(post(CURRENCIES), CurrencyFixtures.createRequest().build(), Caller.ADMIN)
                .andExpect(status().isCreated())));
        UUID languageId = idOf(body(send(post(LANGUAGES), LanguageFixtures.createRequest().build(), Caller.ADMIN)
                .andExpect(status().isCreated())));

        JsonNode country = create(CountryFixtures.createRequest()
                .defaultCurrencyId(currencyId).defaultLanguageId(languageId).build());

        JsonNode read = read(idOf(country));
        assertThat(read.path("defaultCurrencyId").asText()).isEqualTo(currencyId.toString());
        assertThat(read.path("defaultLanguageId").asText()).isEqualTo(languageId.toString());
    }

    @Test
    void anUnknownOrDeletedDefaultCurrencyIsNotFoundAndCreatesNothing() throws Exception {
        UUID deletedCurrency = idOf(body(send(post(CURRENCIES), CurrencyFixtures.createRequest().build(), Caller.ADMIN)
                .andExpect(status().isCreated())));
        mvc.perform(delete(CURRENCIES + "/" + deletedCurrency).with(Caller.ADMIN.request())).andExpect(status().isOk());
        published.reset();

        for (UUID currencyId : new UUID[]{UUID.randomUUID(), deletedCurrency}) {
            CreateCountryRequest request = CountryFixtures.createRequest().defaultCurrencyId(currencyId).build();
            send(post(basePath()), request, Caller.ADMIN).andExpect(status().isNotFound());
            assertThat(jdbc.queryForObject("select count(*) from countries where alpha2 = ?", Integer.class,
                    request.getAlpha2())).isZero();
        }
        published.assertNothingPublished();
    }

    // --- PLT-1: codes stay unique ---------------------------------------------------------------------------------

    @Test
    void refusesEachCodeASeededCountryAlreadyHas() throws Exception {
        CreateCountryRequest[] clashes = {
                CountryFixtures.createRequest().alpha2("RO").build(),
                CountryFixtures.createRequest().alpha3("ROU").build(),
                CountryFixtures.createRequest().numericCode("642").build()};

        for (CreateCountryRequest clash : clashes) {
            send(post(basePath()), clash, Caller.ADMIN).andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    void aDeletedCountryKeepsItsCodesReserved() throws Exception {
        CreateCountryRequest first = validCreate();
        UUID id = idOf(create(first));
        mvc.perform(delete(basePath() + "/" + id).with(Caller.ADMIN.request())).andExpect(status().isOk());

        send(post(basePath()), CountryFixtures.createRequest().alpha2(first.getAlpha2()).build(), Caller.ADMIN)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void theDatabaseRefusesADuplicateCodeTheServiceDidNotCatch() {
        CreateCountryRequest first = validCreate();
        countryService.create(first);

        // Straight to the table, as a second instance racing past the service's check would.
        assertThatThrownBy(() -> jdbc.update("""
                insert into countries (id, alpha2, alpha3, numeric_code, name, is_eu_vat_member, is_eu_member,
                    is_eea_member, is_schengen_member, created_at, updated_at)
                values (gen_random_uuid(), ?, 'QZZ', '999', 'Racer', false, false, false, false, now(), now())
                """, first.getAlpha2()))
                .hasMessageContaining("uk_country_alpha2");
    }
}
