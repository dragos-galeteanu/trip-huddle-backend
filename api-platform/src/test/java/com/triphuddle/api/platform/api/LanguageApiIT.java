package com.triphuddle.api.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.triphuddle.amqp.platform.LanguageCreatedContract;
import com.triphuddle.amqp.platform.LanguageCreatedEvent;
import com.triphuddle.amqp.platform.LanguageDeletedContract;
import com.triphuddle.amqp.platform.LanguageDeletedEvent;
import com.triphuddle.amqp.platform.LanguageDisabledContract;
import com.triphuddle.amqp.platform.LanguageDisabledEvent;
import com.triphuddle.api.platform.dto.CreateLanguageRequest;
import com.triphuddle.api.platform.dto.UpdateLanguageRequest;
import com.triphuddle.api.platform.fixtures.LanguageFixtures;
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
class LanguageApiIT extends AbstractCrudApiIT<CreateLanguageRequest, UpdateLanguageRequest> {

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteTestRows() {
        PlatformTestData.deleteTestRows(jdbc);
    }

    @Override
    protected String basePath() {
        return "/api/v1/platform/languages";
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return PlatformMatrices.REFERENCE_DATA;
    }

    @Override
    protected CreateLanguageRequest validCreate() {
        return LanguageFixtures.createRequest().build();
    }

    @Override
    protected UpdateLanguageRequest validUpdate(UUID id) {
        return LanguageFixtures.updateRequest(id).build();
    }

    @Override
    protected Map<String, CreateLanguageRequest> invalidCreates() {
        return Map.of(
                "one-letter code", LanguageFixtures.createRequest().code("z").build(),
                "nine-letter code", LanguageFixtures.createRequest().code("zzzzzzzzz").build(),
                "blank name", LanguageFixtures.createRequest().name(" ").build(),
                "blank native name", LanguageFixtures.createRequest().nativeName(" ").build(),
                "no writing direction", LanguageFixtures.createRequest().isRightToLeft(null).build());
    }

    @Override
    protected Optional<CreateLanguageRequest> clashingCreate(CreateLanguageRequest existing) {
        return Optional.of(LanguageFixtures.createRequest().code(existing.getCode()).build());
    }

    @Override
    protected void assertUpdated(JsonNode after, UpdateLanguageRequest update) {
        assertThat(after.path("code").asText()).isEqualTo(update.getCode());
        assertThat(after.path("name").asText()).isEqualTo(update.getName());
        assertThat(after.path("nativeName").asText()).isEqualTo(update.getNativeName());
        assertThat(after.path("isRightToLeft").asBoolean()).isTrue();
    }

    @Test
    void publishesCreatedDisabledAndDeleted() throws Exception {
        JsonNode created = create(validCreate());
        UUID id = idOf(created);
        String code = created.path("code").asText();
        mvc.perform(delete(basePath() + "/" + id + "/disable").with(Caller.ADMIN.request())).andExpect(status().isOk());
        mvc.perform(delete(basePath() + "/" + id).with(Caller.ADMIN.request())).andExpect(status().isOk());

        published.assertPublished(new LanguageCreatedEvent(id, code), LanguageCreatedContract.LANGUAGE_CREATED_QUEUE_NAME);
        published.assertPublished(new LanguageDisabledEvent(id, code), LanguageDisabledContract.LANGUAGE_DISABLED_QUEUE_NAME);
        published.assertPublished(new LanguageDeletedEvent(id, code), LanguageDeletedContract.LANGUAGE_DELETED_QUEUE_NAME);
    }

    @Test
    void refusesACodeASeededLanguageAlreadyHas() throws Exception {
        send(post(basePath()), LanguageFixtures.createRequest().code("en").build(), Caller.ADMIN)
                .andExpect(status().isUnprocessableEntity());
    }
}
