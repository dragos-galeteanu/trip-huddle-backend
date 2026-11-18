package com.triphuddle.api.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.platform.CurrencyCreatedContract;
import com.triphuddle.amqp.platform.CurrencyCreatedEvent;
import com.triphuddle.amqp.platform.CurrencyDeletedContract;
import com.triphuddle.amqp.platform.CurrencyDeletedEvent;
import com.triphuddle.amqp.platform.CurrencyDisabledContract;
import com.triphuddle.amqp.platform.CurrencyDisabledEvent;
import com.triphuddle.api.platform.dto.CreateCurrencyRequest;
import com.triphuddle.api.platform.dto.UpdateCurrencyRequest;
import com.triphuddle.api.platform.fixtures.CurrencyFixtures;
import com.triphuddle.api.platform.fixtures.PlatformTestData;
import com.triphuddle.api.platform.security.PlatformMatrices;
import com.triphuddle.testsupport.api.AbstractCrudApiIT;
import com.triphuddle.testsupport.containers.MongoContainerConfig;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(MongoContainerConfig.class)
class CurrencyApiIT extends AbstractCrudApiIT<CreateCurrencyRequest, UpdateCurrencyRequest> {

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteTestRows() {
        PlatformTestData.deleteTestRows(jdbc);
    }

    @Override
    protected String basePath() {
        return "/api/v1/platform/currencies";
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return PlatformMatrices.REFERENCE_DATA;
    }

    @Override
    protected CreateCurrencyRequest validCreate() {
        return CurrencyFixtures.createRequest().build();
    }

    @Override
    protected UpdateCurrencyRequest validUpdate(UUID id) {
        return CurrencyFixtures.updateRequest(id).build();
    }

    @Override
    protected Map<String, CreateCurrencyRequest> invalidCreates() {
        return Map.of(
                "lower-case code", CurrencyFixtures.createRequest().code("qba").build(),
                "two-letter code", CurrencyFixtures.createRequest().code("QB").build(),
                "blank name", CurrencyFixtures.createRequest().name(" ").build(),
                "symbol over 8 characters", CurrencyFixtures.createRequest().symbol("123456789").build(),
                "no symbol position", CurrencyFixtures.createRequest().symbolPosition(null).build(),
                "negative minor units", CurrencyFixtures.createRequest().minorUnits(-1).build());
    }

    @Override
    protected Optional<CreateCurrencyRequest> clashingCreate(CreateCurrencyRequest existing) {
        return Optional.of(CurrencyFixtures.createRequest().code(existing.getCode()).build());
    }

    @Override
    protected void assertUpdated(JsonNode after, UpdateCurrencyRequest update) {
        assertThat(after.path("code").asText()).isEqualTo(update.getCode());
        assertThat(after.path("name").asText()).isEqualTo(update.getName());
        assertThat(after.path("symbolPosition").asText()).isEqualTo(update.getSymbolPosition().name());
        assertThat(after.path("minorUnits").asInt()).isEqualTo(update.getMinorUnits());
    }

    @Test
    void publishesCreatedDisabledAndDeleted() throws Exception {
        JsonNode created = create(validCreate());
        UUID id = idOf(created);
        String code = created.path("code").asText();
        mvc.perform(delete(basePath() + "/" + id + "/disable").with(Caller.ADMIN.request())).andExpect(status().isOk());
        mvc.perform(delete(basePath() + "/" + id).with(Caller.ADMIN.request())).andExpect(status().isOk());

        published.assertPublished(new CurrencyCreatedEvent(id, code), CurrencyCreatedContract.CURRENCY_CREATED_QUEUE_NAME);
        published.assertPublished(new CurrencyDisabledEvent(id, code), CurrencyDisabledContract.CURRENCY_DISABLED_QUEUE_NAME);
        published.assertPublished(new CurrencyDeletedEvent(id, code), CurrencyDeletedContract.CURRENCY_DELETED_QUEUE_NAME);
    }

    @Test
    void refusesACodeASeededCurrencyAlreadyHas() throws Exception {
        send(post(basePath()), CurrencyFixtures.createRequest().code("EUR").build(), Caller.ADMIN)
                .andExpect(status().isUnprocessableEntity());
    }
}
