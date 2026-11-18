package com.triphuddle.testsupport.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triphuddle.testsupport.IntegrationTest;
import com.triphuddle.testsupport.messaging.RecordingMessageProducer;
import com.triphuddle.testsupport.security.AuthorizationMatrix;
import com.triphuddle.testsupport.security.Caller;
import com.triphuddle.testsupport.security.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Everything a {@code BaseJpaController} resource does over HTTP, on the real stack: security, validation, service,
 * Flyway schema and Postgres. A resource extends it, supplies its fixtures, and adds what only it does.
 *
 * @param <C> the create request
 * @param <U> the update request
 */
@IntegrationTest
public abstract class AbstractCrudApiIT<C, U> {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected RecordingMessageProducer published;

    /**
     * The resource's path, e.g. {@code /api/v1/platform/countries}.
     */
    protected abstract String basePath();

    protected abstract AuthorizationMatrix matrix();

    /**
     * A valid create request, unique on every call so tests never clash.
     */
    protected abstract C validCreate();

    /**
     * A valid update of the resource with this ID.
     */
    protected abstract U validUpdate(UUID id);

    /**
     * Create requests that must be refused with 400, each named by what is wrong with it.
     */
    protected abstract Map<String, C> invalidCreates();

    /**
     * A create that clashes with an existing resource made from the given request, or empty if none can.
     */
    protected abstract Optional<C> clashingCreate(C existing);

    /**
     * Check the resource read back after {@link #validUpdate} shows the update.
     */
    protected abstract void assertUpdated(JsonNode after, U update);

    /**
     * The one user who creates and changes resources in the generic tests; the matrix test calls as strangers.
     */
    protected final UUID actorId = UUID.randomUUID();

    /**
     * The actor's platform role; ADMIN unless a resource's own rules need another.
     */
    protected Caller actorRole() {
        return Caller.ADMIN;
    }

    protected RequestPostProcessor actor() {
        return actorRole().request(actorId);
    }

    /**
     * Who performs an operation on an existing resource in the generic tests: the actor, unless only someone else may,
     * such as the owner of a profile being the only one who deletes it.
     */
    protected RequestPostProcessor actorFor(Operation operation, UUID id) {
        return actor();
    }

    /**
     * What the actor gets for an ID that doesn't exist: 404, or 403 where the policy decides by the resource it
     * looks up and so refuses before the service could say it is missing.
     */
    protected int unknownIdStatus(Operation operation) {
        return 404;
    }

    @BeforeEach
    void forgetEarlierMessages() {
        published.reset();
    }

    // --- the happy path -------------------------------------------------------------------------------------------

    @Test
    void createsAResourceAndReadsItBack() throws Exception {
        JsonNode created = create(validCreate());

        assertThat(created.path("id").asText()).isNotBlank();
        assertThat(created.path("createdAt").isNull()).isFalse();
        assertThat(created.path("disabledAt").isNull()).isTrue();
        assertThat(read(idOf(created))).isEqualTo(created);
    }

    @Test
    void updatesAResource() throws Exception {
        UUID id = idOf(create(validCreate()));
        U update = validUpdate(id);

        send(put(basePath()), update, actorFor(Operation.UPDATE, id)).andExpect(status().isOk());

        assertUpdated(read(id), update);
    }

    @Test
    void listsResourcesPageByPage() throws Exception {
        for (int i = 0; i < 3; i++) {
            create(validCreate());
        }

        mvc.perform(get(basePath()).param("page", "1").param("pageSize", "2").with(actor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.metaData.pageSize").value(2))
                .andExpect(jsonPath("$.metaData.currentPage").value(1))
                .andExpect(result -> assertThat(json.readTree(result.getResponse().getContentAsByteArray())
                        .at("/metaData/totalCount").asLong()).isGreaterThanOrEqualTo(3));
    }

    @Test
    void disablingStampsDisabledAt() throws Exception {
        UUID id = idOf(create(validCreate()));

        JsonNode disabled = body(mvc.perform(delete(basePath() + "/" + id + "/disable")
                        .with(actorFor(Operation.DISABLE, id)))
                .andExpect(status().isOk()));

        assertThat(disabled.path("disabledAt").isNull()).isFalse();
    }

    @Test
    void deletingStampsDeletedAt() throws Exception {
        UUID id = idOf(create(validCreate()));

        JsonNode deleted = body(mvc.perform(delete(basePath() + "/" + id).with(actorFor(Operation.DELETE, id)))
                .andExpect(status().isOk()));

        assertThat(deleted.path("deletedAt").isNull()).isFalse();
    }

    @Test
    void aDeletedResourceIsGoneForEveryOperationAndFromTheList() throws Exception {
        UUID id = idOf(create(validCreate()));
        mvc.perform(delete(basePath() + "/" + id).with(actorFor(Operation.DELETE, id))).andExpect(status().isOk());

        assertGoneEverywhere(id);
        assertThat(listedIds()).doesNotContain(id.toString());
    }

    // --- failures -------------------------------------------------------------------------------------------------

    @Test
    void anUnknownIdIsNotFoundForEveryOperation() throws Exception {
        assertNotFoundEverywhere(UUID.randomUUID());
    }

    @Test
    void aMalformedIdIsABadRequest() throws Exception {
        mvc.perform(get(basePath() + "/not-a-uuid").with(actor()))
                .andExpect(status().isBadRequest());
    }

    @TestFactory
    Stream<DynamicTest> invalidCreatesAreBadRequests() {
        return invalidCreates().entrySet().stream().map(invalid -> dynamicTest(invalid.getKey(), () ->
                send(post(basePath()), invalid.getValue(), actor())
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))));
    }

    @Test
    void aClashingCreateIsUnprocessableAndPublishesNothing() throws Exception {
        C first = validCreate();
        create(first);
        Optional<C> clash = clashingCreate(first);
        assumeThat(clash).as("the resource has no unique fields").isPresent();
        published.reset();

        send(post(basePath()), clash.get(), actor()).andExpect(status().isUnprocessableEntity());

        published.assertNothingPublished();
    }

    @Test
    void aPageOutOfRangeIsABadRequest() throws Exception {
        mvc.perform(get(basePath()).param("page", "0").param("pageSize", "10").with(actor()))
                .andExpect(status().isBadRequest());
        mvc.perform(get(basePath()).param("page", "1").param("pageSize", "101").with(actor()))
                .andExpect(status().isBadRequest());
    }

    // --- security, from the same matrix as the policy's own test --------------------------------------------------

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("com.triphuddle.testsupport.security.AuthorizationMatrix#everyOperationAndCaller")
    void answersOverHttpAsTheMatrixSays(Operation operation, Caller caller) throws Exception {
        UUID id = idOf(create(validCreate()));

        int status = perform(operation, id, caller).andReturn().getResponse().getStatus();

        if (matrix().isAllowed(operation, caller)) {
            assertThat(status).as("%s by %s", operation, caller).isBetween(200, 299);
        } else if (caller == Caller.ANONYMOUS) {
            assertThat(status).as("%s by %s", operation, caller).isIn(401, 403);
        } else {
            assertThat(status).as("%s by %s", operation, caller).isEqualTo(403);
        }
    }

    // --- helpers for subclasses -----------------------------------------------------------------------------------

    /**
     * Create as an admin and return the response body.
     */
    protected JsonNode create(C request) throws Exception {
        return body(send(post(basePath()), request, actor()).andExpect(status().isCreated()));
    }

    /**
     * Read one by ID as an admin.
     */
    protected JsonNode read(UUID id) throws Exception {
        return body(mvc.perform(get(basePath() + "/" + id).with(actor())).andExpect(status().isOk()));
    }

    protected ResultActions send(MockHttpServletRequestBuilder request, Object body, Caller caller) throws Exception {
        return send(request, body, caller.request());
    }

    protected ResultActions send(MockHttpServletRequestBuilder request, Object body, RequestPostProcessor caller)
            throws Exception {
        return mvc.perform(request.with(caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    protected static UUID idOf(JsonNode resource) {
        return UUID.fromString(resource.path("id").asText());
    }

    private ResultActions perform(Operation operation, UUID id, Caller caller) throws Exception {
        return perform(operation, id, caller.request());
    }

    private ResultActions perform(Operation operation, UUID id, RequestPostProcessor caller) throws Exception {
        return switch (operation) {
            case CREATE -> send(post(basePath()), validCreate(), caller);
            case GET_BY_ID -> mvc.perform(get(basePath() + "/" + id).with(caller));
            case GET_ALL -> mvc.perform(get(basePath()).param("page", "1").param("pageSize", "5").with(caller));
            case UPDATE -> send(put(basePath()), validUpdate(id), caller);
            case DISABLE -> mvc.perform(delete(basePath() + "/" + id + "/disable").with(caller));
            case DELETE -> mvc.perform(delete(basePath() + "/" + id).with(caller));
        };
    }

    private void assertNotFoundEverywhere(UUID id) throws Exception {
        for (Operation operation : new Operation[]{Operation.GET_BY_ID, Operation.UPDATE, Operation.DISABLE,
                Operation.DELETE}) {
            int status = perform(operation, id, actorFor(operation, id)).andReturn().getResponse().getStatus();
            assertThat(status).as(operation.name()).isEqualTo(unknownIdStatus(operation));
        }
    }

    /**
     * Assert a deleted resource is gone for every operation the actor may still attempt.
     */
    private void assertGoneEverywhere(UUID id) throws Exception {
        for (Operation operation : new Operation[]{Operation.GET_BY_ID, Operation.UPDATE, Operation.DISABLE,
                Operation.DELETE}) {
            int status = perform(operation, id, actorFor(operation, id)).andReturn().getResponse().getStatus();
            assertThat(status).as(operation.name()).isIn(404, unknownIdStatus(operation));
        }
    }

    private Stream<String> listedIdsOnPage(int page) throws Exception {
        JsonNode list = body(mvc.perform(get(basePath()).param("page", String.valueOf(page)).param("pageSize", "100")
                .with(actor())).andExpect(status().isOk()));
        return Stream.iterate(0, i -> i < list.path("data").size(), i -> i + 1)
                .map(i -> list.path("data").get(i).path("id").asText());
    }

    private List<String> listedIds() throws Exception {
        JsonNode first = body(mvc.perform(get(basePath()).param("page", "1").param("pageSize", "100")
                .with(actor())).andExpect(status().isOk()));
        int pages = first.at("/metaData/totalPages").asInt();
        List<String> ids = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            listedIdsOnPage(page).forEach(ids::add);
        }
        return ids;
    }
}
