# Testing strategy

A plan for unit, integration and smoke tests across the services: shared building blocks in a `test-support`
module, the tests themselves in each service, and end-to-end smoke tests in their own module. api-platform is the
worked example. The decisions taken are collected at the end.

## Where we start

- No tests exist yet in any module.
- `common` already builds a test-jar, and six services already depend on it (`<classifier>tests</classifier>`), so
  shared test code has a home.
- `spring-boot-starter-test`, `spring-security-test` and the Testcontainers BOM (1.21.3) are already in the build.
  JaCoCo is in the local Maven cache but not configured.
- Every Postgres service now has Flyway migrations, so integration tests can build the schema exactly as production
  does: Flyway runs on an empty container, then Hibernate's `ddl-auto: validate` checks the entities against it.

## Principles

1. **Two kinds of test, split by name.** `*Test` are unit tests: no Spring context, no Docker, milliseconds each,
   run by Surefire. `*IT` are integration tests: a real Spring context against real Postgres/Mongo/RabbitMQ in
   Testcontainers, run by Failsafe. `mvn test` stays fast, and `mvn verify` runs everything.
2. **Test behaviour through the public seam.** Services are tested through their public methods with their
   collaborators mocked. Controllers, security, validation and persistence are tested together over HTTP, because
   most bugs there sit between the layers (a `@PreAuthorize` expression naming the wrong bean, a 500 where a 409 was
   meant).
3. **Write the generic part once.** Every resource built on `BaseJpaController` / `BaseJpaService` /
   `BaseAuthorizationPolicy` behaves the same for create, get, list, update, disable and delete. That behaviour is
   tested by abstract base tests in `common`. A new resource gets them by extending one class and supplying its
   fixtures, so coverage grows with the code instead of lagging behind it.
4. **Rules the compiler can't enforce become ArchUnit rules.** For example, every endpoint must have `@PreAuthorize`.
   The rules live in `common`, and each service runs them with one line.
5. **One container per kind per JVM.** Containers start once and are shared by every IT in the module. Each test
   cleans up what it wrote, so the suite stays fast as it grows.

## Building blocks in `test-support`

A new module, `test-support`, holds the shared test code as ordinary main-scope code (package
`com.triphuddle.testsupport`). It depends on `common`, `amqp`, `spring-boot-starter-test`, `spring-security-test`,
`spring-boot-testcontainers`, the Testcontainers modules, ArchUnit and Awaitility, so a service adds one
test-scoped dependency and gets all of them. It is the "common" home for testing, in a module of its own because
Maven doesn't pass a test-jar's dependencies on to the modules that use it. `common`'s own tests stay in `common`,
and the services' dependencies on `common`'s test-jar go away.

```xml
<dependency>
    <groupId>com.triphuddle</groupId>
    <artifactId>test-support</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

| Class | What it gives a service |
|---|---|
| `@IntegrationTest` | Meta-annotation: `@SpringBootTest(webEnvironment = MOCK)`, `@AutoConfigureMockMvc`, `@ActiveProfiles("it")`, `@Import(PostgresContainerConfig.class)` |
| `PostgresContainerConfig` | A singleton Postgres container (the project's PostGIS + pgvector image) wired with `@ServiceConnection`; Flyway runs on it at context start |
| `MongoContainerConfig` | The same for MongoDB, for chat, user, platform and notification |
| `TestAuth` | Request post-processors for each role: `TestAuth.admin()`, `TestAuth.user(id)`, `TestAuth.anonymous()`. They go through the real `FirebaseJwtGrantedAuthoritiesConverter`, so role parsing is exercised too |
| `AuthorizationMatrix` + `AbstractAuthorizationPolicyTest<P>` | A service declares who may do what; the base test checks every operation × every role (and anonymous), allowed and denied |
| `AbstractCrudApiIT<C, U, R>` | Every CRUD behaviour over HTTP: 201/200/404/400/401/403/422, pagination, disable, delete, security matrix. A resource supplies fixtures and gets about 25 tests |
| `ArchitectureRules` | ArchUnit rules: endpoints have `@PreAuthorize`, controllers don't touch repositories, entities extend `BaseJpaEntity`, no field injection |
| `JsonAssertions` | Helpers to read `Paginated<R>` and `ErrorResponse` bodies |

| `RecordingMessageProducer` | Replaces `RabbitMQMessageProducer` in API ITs: records instead of sending, with `assertPublished(event, queue)` and `assertNothingPublished()` |
| `RabbitContainerConfig` | A real RabbitMQ container, imported only by messaging ITs, which send a real message and check the listener's effect. That covers JSON serialisation, queue names and the dead-letter setup |

## In each service

```
src/test/java/com/triphuddle/api/<service>/
├── ArchitectureTest.java                  # applies ArchitectureRules
├── fixtures/                              # test data builders: CountryFixtures, ...
├── security/<Resource>AuthorizationPolicyTest.java
├── service/<Resource>ServiceTest.java     # Mockito, business rules and events
├── mapper/<Resource>MapperTest.java       # the generated MapStruct implementation, no Spring
├── api/<Resource>ApiIT.java               # extends AbstractCrudApiIT, plus resource-specific cases
├── repository/<Resource>RepositoryIT.java # only for custom queries (native SQL, @Query)
├── messaging/<Listener>IT.java            # consumers: message in, state and events out
└── migration/MigrationIT.java             # seeds and data migrations (schema is already checked by every IT)
src/test/resources/application-it.yml      # turns off Eureka, Zipkin and anything that dials out
```

## Build changes (root `pom.xml`)

```xml
<build>
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-failsafe-plugin</artifactId>
        <executions>
          <execution><goals><goal>integration-test</goal><goal>verify</goal></goals></execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
        <version>0.8.12</version>
        <configuration>
          <excludes>
            <exclude>**/*Application.class</exclude>
            <exclude>**/dto/**</exclude>
            <exclude>**/config/*Properties.class</exclude>
          </excludes>
        </configuration>
        <executions>
          <execution><id>agent-unit</id><goals><goal>prepare-agent</goal></goals></execution>
          <execution><id>agent-it</id><goals><goal>prepare-agent-integration</goal></goals></execution>
          <execution><id>merge</id><phase>verify</phase><goals><goal>merge</goal></goals>
            <configuration>
              <fileSets><fileSet><directory>${project.build.directory}</directory>
                <includes><include>jacoco*.exec</include></includes></fileSet></fileSets>
              <destFile>${project.build.directory}/jacoco-merged.exec</destFile>
            </configuration>
          </execution>
          <execution><id>report</id><phase>verify</phase><goals><goal>report</goal></goals>
            <configuration><dataFile>${project.build.directory}/jacoco-merged.exec</dataFile></configuration>
          </execution>
          <execution><id>gate</id><phase>verify</phase><goals><goal>check</goal></goals>
            <configuration>
              <dataFile>${project.build.directory}/jacoco-merged.exec</dataFile>
              <rules><rule><element>BUNDLE</element><limits>
                <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.85</minimum></limit>
                <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.75</minimum></limit>
              </limits></rule></rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </pluginManagement>
</build>
```

Also, in the `<dependencyManagement>` of the root `pom.xml`: `com.tngtech.archunit:archunit-junit5`. Each service adds
`spring-boot-testcontainers`, `testcontainers:postgresql` (and `mongodb` / `rabbitmq` as needed) and
`archunit-junit5`, all in test scope. The thresholds are explained under Decisions.

## Worked example: api-platform

api-platform has three JPA resources (Country, Currency, Language) of the same shape, a Mongo document (FeatureFlag),
events for each lifecycle change, public reads with admin-only writes, and a seed migration. It is small enough to
show completely and has every layer the other services have.

### 1. Shared: the authorization matrix (`test-support`)

```java
package com.triphuddle.testsupport.security;

/** The operations every BaseAuthorizationPolicy answers for. */
public enum Operation { CREATE, GET_BY_ID, GET_ALL, UPDATE, DISABLE, DELETE }

/** Who may do what; anyone not listed for an operation must be refused it. */
public final class AuthorizationMatrix {

    private final Map<Operation, Set<Caller>> allowed = new EnumMap<>(Operation.class);

    public static AuthorizationMatrix create() {
        return new AuthorizationMatrix();
    }

    public AuthorizationMatrix allow(Operation operation, Caller... callers) {
        allowed.computeIfAbsent(operation, o -> EnumSet.noneOf(Caller.class)).addAll(List.of(callers));
        return this;
    }

    public AuthorizationMatrix allowEveryone(Operation operation) {
        return allow(operation, Caller.values());
    }

    public boolean isAllowed(Operation operation, Caller caller) {
        return allowed.getOrDefault(operation, Set.of()).contains(caller);
    }
}
```

`Caller` is `ANONYMOUS, USER, ADMIN, SUPPORT, INSPECTOR, SALES`. Each one knows how to build the `Authentication`
it stands for: an `AnonymousAuthenticationToken`, or a `JwtAuthenticationToken` whose authorities come from the real
`FirebaseJwtGrantedAuthoritiesConverter`.

```java
package com.triphuddle.testsupport.security;

/**
 * Checks a policy against its matrix for every operation and caller, allowed and denied alike.
 */
public abstract class AbstractAuthorizationPolicyTest<C, U, P extends BaseAuthorizationPolicy<C, U, UUID>> {

    protected abstract P policy();

    protected abstract AuthorizationMatrix matrix();

    protected abstract C createRequest();

    protected abstract U updateRequest();

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("everyOperationAndCaller")
    void answersAsTheMatrixSays(Operation operation, Caller caller) {
        Authentication auth = caller.authentication();
        UUID id = UUID.randomUUID();
        boolean granted = switch (operation) {
            case CREATE -> policy().canCreate(auth, createRequest());
            case GET_BY_ID -> policy().canGetById(auth, id);
            case GET_ALL -> policy().canGetAll(auth);
            case UPDATE -> policy().canUpdate(auth, updateRequest());
            case DISABLE -> policy().canDisable(auth);
            case DELETE -> policy().canDelete(auth, id);
        };
        assertThat(granted).as("%s by %s", operation, caller).isEqualTo(matrix().isAllowed(operation, caller));
    }

    @Test
    void refusesWritesWithoutAuthentication() {
        assertThat(policy().canCreate(null, createRequest())).isFalse();
        assertThat(policy().canUpdate(null, updateRequest())).isFalse();
        assertThat(policy().canDelete(null, UUID.randomUUID())).isFalse();
    }

    static Stream<Arguments> everyOperationAndCaller() {
        return Arrays.stream(Operation.values())
                .flatMap(o -> Arrays.stream(Caller.values()).map(c -> Arguments.of(o, c)));
    }
}
```

### 2. Platform: the policy test (36 cases from about 20 lines)

```java
package com.triphuddle.api.platform.security;

class CountryAuthorizationPolicyTest
        extends AbstractAuthorizationPolicyTest<CreateCountryRequest, UpdateCountryRequest, CountryAuthorizationPolicy> {

    @Override
    protected CountryAuthorizationPolicy policy() {
        return new CountryAuthorizationPolicy();
    }

    @Override
    protected AuthorizationMatrix matrix() {
        return AuthorizationMatrix.create()
                .allowEveryone(GET_BY_ID)
                .allowEveryone(GET_ALL)
                .allow(CREATE, ADMIN, SUPPORT)
                .allow(UPDATE, ADMIN, SUPPORT)
                .allow(DISABLE, ADMIN, SUPPORT)
                .allow(DELETE, ADMIN, SUPPORT);
    }

    @Override
    protected CreateCountryRequest createRequest() {
        return CountryFixtures.createRequest().build();
    }

    @Override
    protected UpdateCountryRequest updateRequest() {
        return CountryFixtures.updateRequest(UUID.randomUUID()).build();
    }
}
```

The matrix doubles as a readable spec of who may do what, matching the "Who may call it" table in the README.

### 3. Platform: test data builders

```java
package com.triphuddle.api.platform.fixtures;

/** Valid countries and requests, to be changed only where a test cares. */
public final class CountryFixtures {

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** A valid request with codes no other call has used, so tests can't collide. */
    public static CreateCountryRequest.CreateCountryRequestBuilder createRequest() {
        int n = SEQ.incrementAndGet();
        return CreateCountryRequest.builder()
                .alpha2(code2(n)).alpha3(code3(n)).numericCode("%03d".formatted(n % 1000))
                .name("Testland " + n).callingCode("+999")
                .standardVatRate(new BigDecimal("19.00"))
                .isEuVatMember(false).isEuMember(false).isEeaMember(false).isSchengenMember(false);
    }

    public static UpdateCountryRequest.UpdateCountryRequestBuilder updateRequest(UUID id) { /* same, with the id */ }

    public static Country entity() { /* a Country with an id and audit timestamps set */ }

    private static String code2(int n) { return "" + (char) ('A' + n / 26 % 26) + (char) ('A' + n % 26); }
    // ...
}
```

The request DTOs get `@Builder(toBuilder = true)` next to their Lombok `@Data`, so a test changes only the field it
cares about: `CountryFixtures.createRequest().alpha2("ro").build()`.

### 4. Platform: the service unit test

Business rules, conflicts and events, with the repository, mapper, other services and producer mocked. The mapper is
the real MapStruct implementation (`Mappers.getMapper`), so a wrong mapping fails here rather than silently.

```java
package com.triphuddle.api.platform.service;

@ExtendWith(MockitoExtension.class)
class CountryServiceTest {

    @Mock CountryRepository countryRepository;
    @Mock CurrencyService currencyService;
    @Mock LanguageService languageService;
    @Mock RabbitMQMessageProducer messageProducer;
    @Spy CountryMapper countryMapper = Mappers.getMapper(CountryMapper.class);
    @InjectMocks CountryService countryService;

    @Nested
    class Create {

        @Test
        void savesTheCountryWithItsDefaultsAndPublishesCreated() {
            Currency eur = CurrencyFixtures.entity("EUR");
            Language fr = LanguageFixtures.entity("fr");
            CreateCountryRequest request = CountryFixtures.createRequest()
                    .defaultCurrencyId(eur.getId()).defaultLanguageId(fr.getId()).build();
            given(currencyService.getEntityById(eur.getId())).willReturn(eur);
            given(languageService.getEntityById(fr.getId())).willReturn(fr);
            given(countryRepository.save(any())).willAnswer(withId());

            CountryResponse response = countryService.create(request);

            assertThat(response.getAlpha2()).isEqualTo(request.getAlpha2());
            assertThat(response.getDefaultCurrencyId()).isEqualTo(eur.getId());
            assertThat(response.getDefaultLanguageId()).isEqualTo(fr.getId());
            then(messageProducer).should().publish(
                    new CountryCreatedEvent(response.getId(), request.getAlpha2()),
                    "", CountryCreatedContract.COUNTRY_CREATED_QUEUE_NAME);
        }

        @Test
        void leavesDefaultsEmptyWhenNotGiven() { /* no currency/language lookups happen */ }

        @ParameterizedTest
        @EnumSource(CountryCode.class) // ALPHA2, ALPHA3, NUMERIC
        void refusesACodeAlreadyTaken(CountryCode taken) {
            CreateCountryRequest request = CountryFixtures.createRequest().build();
            taken.stubExists(countryRepository, request);

            assertThatThrownBy(() -> countryService.create(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(taken.value(request));
            then(countryRepository).should(never()).save(any());
            then(messageProducer).shouldHaveNoInteractions();
        }

        @Test
        void failsWhenTheDefaultCurrencyDoesNotExist() {
            // currencyService throws ResourceNotFoundException -> nothing saved, nothing published
        }
    }

    @Nested
    class Update {
        @Test void appliesTheChangesAndPublishesNothing() { }
        @Test void allowsKeepingItsOwnCodes() { /* existsBy...AndIdNot is asked with its own id */ }
        @ParameterizedTest @EnumSource(CountryCode.class) void refusesACodeTakenByAnother(CountryCode taken) { }
        @Test void failsForAnUnknownId() { }
    }

    @Nested
    class DisableAndDelete {
        @Test void disableStampsDisabledAtAndPublishesDisabled() { }
        @Test void deleteStampsDeletedAtAndPublishesDeleted() { }
        @Test void bothFailForAnUnknownId() { }
        @Test void aDeletedCountryIsNotFoundForReadUpdateDisableAndDelete() { /* decided: deleted is gone */ }
    }

    @Test
    void getAllAsksForTheRequestedPage() {
        // PaginationFilter(page 2, size 10) -> PageRequest.of(1, 10); maps the content and metadata
    }
}
```

### 5. Shared: the CRUD API base (`test-support`)

```java
package com.triphuddle.testsupport.api;

/**
 * Everything a BaseJpaController resource does over HTTP, against the real stack: security, validation, service,
 * Flyway schema and Postgres. A resource extends it and supplies fixtures.
 */
@IntegrationTest
public abstract class AbstractCrudApiIT<C, U, R> {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    /** e.g. "/api/v1/platform/countries" */
    protected abstract String basePath();

    protected abstract AuthorizationMatrix matrix();

    /** A valid create request, unique on every call. */
    protected abstract C validCreate();

    /** A valid update of the resource with this id. */
    protected abstract U validUpdate(UUID id);

    /** Requests that must be refused with 400, each named by what is wrong with it. */
    protected abstract Map<String, C> invalidCreates();

    /** A create that clashes with an existing resource, given that resource's create; empty if none can. */
    protected abstract Optional<C> clashingCreate(C existing);

    protected abstract UUID idOf(R response);

    protected abstract Class<R> responseType();

    // --- happy path ----------------------------------------------------------------------------------------------

    @Test
    void createsThenReadsBackTheSameResource() throws Exception {
        R created = create(validCreate());
        R read = get(idOf(created), Caller.ADMIN);
        assertThat(read).usingRecursiveComparison().isEqualTo(created);
    }

    @Test
    void updatesAResource() throws Exception {
        R created = create(validCreate());
        U update = validUpdate(idOf(created));
        mvc.perform(put(basePath()).with(Caller.ADMIN.request()).contentType(JSON).content(json.writeValueAsBytes(update)))
                .andExpect(status().isOk());
        assertUpdated(get(idOf(created), Caller.ADMIN), update);
    }

    @Test
    void listsPageByPage() throws Exception {
        for (int i = 0; i < 3; i++) create(validCreate());
        mvc.perform(get(basePath()).param("page", "1").param("pageSize", "2").with(Caller.ADMIN.request()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.metaData.totalElements").value(greaterThanOrEqualTo(3)));
    }

    @Test void disableStampsDisabledAt() throws Exception { /* 200, disabledAt set */ }
    @Test void deleteStampsDeletedAt() throws Exception { /* 200, deletedAt set */ }
    @Test void aDeletedResourceIs404EverywhereAndLeavesTheList() throws Exception { }

    // --- failures -----------------------------------------------------------------------------------------------

    @Test void unknownIdIs404ForRead() throws Exception { /* GET /{random} -> 404 ErrorResponse */ }
    @Test void unknownIdIs404ForUpdateDisableDelete() throws Exception { }
    @Test void malformedIdIs400() throws Exception { /* GET /not-a-uuid -> 400 */ }

    @TestFactory
    Stream<DynamicTest> invalidCreatesAre400() {
        return invalidCreates().entrySet().stream().map(e -> dynamicTest(e.getKey(), () ->
                mvc.perform(post(basePath()).with(Caller.ADMIN.request()).contentType(JSON)
                                .content(json.writeValueAsBytes(e.getValue())))
                        .andExpect(status().isBadRequest())));
    }

    @Test
    void clashingCreateIs422() throws Exception {
        C first = validCreate();
        create(first);
        Assumptions.assumeTrue(clashingCreate(first).isPresent());
        mvc.perform(post(basePath()).with(Caller.ADMIN.request()).contentType(JSON)
                        .content(json.writeValueAsBytes(clashingCreate(first).get())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void paginationOutOfRangeIs400() throws Exception { /* page=0, pageSize=101 */ }

    // --- security, from the same matrix as the policy test -------------------------------------------------------

    @ParameterizedTest(name = "{0} by {1}")
    @MethodSource("com.triphuddle.testsupport.security.AuthorizationMatrix#everyOperationAndCaller")
    void httpAnswersAsTheMatrixSays(Operation operation, Caller caller) throws Exception {
        R existing = create(validCreate());
        int status = perform(operation, idOf(existing), caller).andReturn().getResponse().getStatus();
        if (matrix().isAllowed(operation, caller)) {
            assertThat(status).as("%s by %s", operation, caller).isBetween(200, 299);
        } else {
            // Anonymous callers are refused with 401 where the route isn't public, 403 where the policy refuses them.
            assertThat(status).as("%s by %s", operation, caller).isIn(401, 403);
        }
    }

    /** Override to check the fields an update changed. */
    protected void assertUpdated(R after, U update) { }

    // create(..), get(..), perform(..) helpers omitted
}
```

### 6. Platform: the API IT

```java
package com.triphuddle.api.platform.api;

class CountryApiIT extends AbstractCrudApiIT<CreateCountryRequest, UpdateCountryRequest, CountryResponse> {

    @Override protected String basePath() { return "/api/v1/platform/countries"; }
    @Override protected AuthorizationMatrix matrix() { return PlatformMatrices.REFERENCE_DATA; }
    @Override protected CreateCountryRequest validCreate() { return CountryFixtures.createRequest().build(); }
    @Override protected UpdateCountryRequest validUpdate(UUID id) { return CountryFixtures.updateRequest(id).name("Renamed").build(); }
    @Override protected UUID idOf(CountryResponse r) { return r.getId(); }
    @Override protected Class<CountryResponse> responseType() { return CountryResponse.class; }

    @Override
    protected Map<String, CreateCountryRequest> invalidCreates() {
        return Map.of(
                "lower-case alpha2", CountryFixtures.createRequest().alpha2("ro").build(),
                "four-letter alpha3", CountryFixtures.createRequest().alpha3("ROUX").build(),
                "numeric code with letters", CountryFixtures.createRequest().numericCode("6A2").build(),
                "blank name", CountryFixtures.createRequest().name(" ").build(),
                "VAT with three decimals", CountryFixtures.createRequest().standardVatRate(new BigDecimal("19.001")).build(),
                "negative VAT", CountryFixtures.createRequest().standardVatRate(new BigDecimal("-1")).build(),
                "missing EU flag", CountryFixtures.createRequest().isEuMember(null).build());
    }

    @Override
    protected Optional<CreateCountryRequest> clashingCreate(CreateCountryRequest existing) {
        return Optional.of(CountryFixtures.createRequest().alpha2(existing.getAlpha2()).build());
    }

    @Override
    protected void assertUpdated(CountryResponse after, UpdateCountryRequest update) {
        assertThat(after.getName()).isEqualTo("Renamed");
    }

    // --- what only countries do ----------------------------------------------------------------------------------

    @Autowired RecordingMessageProducer published;

    @Test
    void createPublishesCountryCreated() throws Exception {
        CountryResponse created = create(validCreate());
        published.assertPublished(new CountryCreatedEvent(created.getId(), created.getAlpha2()),
                CountryCreatedContract.COUNTRY_CREATED_QUEUE_NAME);
    }

    @Test
    void aCreateThatFailsAtCommitPublishesNothing() throws Exception {
        // Two concurrent creates of one alpha2: the loser fails on uk_country_alpha2 at commit, so only the winner's
        // CountryCreatedEvent may be recorded (NFR-4: events go out after commit).
    }

    @Test
    void linksItsDefaultCurrencyAndLanguage() throws Exception {
        // create a currency and a language through their APIs, then a country pointing at them; read it back
    }

    @Test
    void anUnknownDefaultCurrencyIs404AndCreatesNothing() throws Exception { }

    @Test
    void readsArePublicWithoutAToken() throws Exception {
        mvc.perform(get(basePath()).param("page", "1").param("pageSize", "10")).andExpect(status().isOk());
    }
}
```

`CurrencyApiIT` and `LanguageApiIT` are the same 30 or so lines each. Adding a resource means adding one class like
this, and it arrives with about 25 generic tests plus its own.

### 7. Platform: the seed migration IT

The schema itself needs no test of its own: every IT context runs Flyway and `ddl-auto: validate` against it. What
needs a test is the data in V2.

```java
package com.triphuddle.api.platform.migration;

@IntegrationTest
class SeedMigrationIT {

    @Autowired JdbcTemplate jdbc;

    @Test
    void seedsEveryIsoCountryCurrencyAndLanguage() {
        assertThat(count("countries")).isEqualTo(249);
        assertThat(count("currencies")).isEqualTo(153);
        assertThat(count("languages")).isEqualTo(77);
    }

    @Test
    void everyCountryButAntarcticaHasADefaultCurrencyAndLanguage() {
        assertThat(jdbc.queryForList("select alpha2 from countries where default_currency_id is null or "
                + "default_language_id is null", String.class)).containsExactly("AQ");
    }

    @Test
    void membershipsMatchTheTreaties() {
        assertThat(alpha2Where("is_eu_member")).hasSize(27).contains("BG", "HR").doesNotContain("GB");
        assertThat(alpha2Where("is_eea_member")).hasSize(30).contains("IS", "LI", "NO");
        assertThat(alpha2Where("is_schengen_member")).hasSize(29).contains("CH").doesNotContain("IE", "CY");
    }

    @Test
    void disablesTheUnservedCountriesAndOnlyTheirUnsharedCurrencies() {
        assertThat(alpha2Where("disabled_at is not null")).hasSize(59).contains("RU", "PS", "NG", "EH")
                .doesNotContain("MA", "EG", "ET", "MG", "CV");
        assertThat(codesWhere("currencies", "disabled_at is not null")).contains("XOF", "XAF", "RUB")
                .doesNotContain("MAD", "ILS", "EUR", "USD");
    }

    @Test
    void reRunningTheSeedChangesNothing() { /* run V2 again through Flyway's resource; counts unchanged */ }
}
```

Because the API ITs share the database with the seed, their fixtures use codes no real country has (`QA`–`QZ`, `XA`–`XZ`
are user-assigned in ISO 3166). That's also why `CountryFixtures` generates unique codes.

### 8. Platform: mapper, Mongo and architecture

```java
class CountryMapperTest {
    private final CountryMapper mapper = Mappers.getMapper(CountryMapper.class);

    @Test void mapsTheMembershipFlagsBothWays() { /* isEuMember <-> euMember, the Lombok boolean trap */ }
    @Test void exposesDefaultIdsButNeverTakesEntitiesFromRequests() { }
    @Test void updateLeavesIdAndAuditFieldsAlone() { }
}

@DataMongoTest
@Import(MongoContainerConfig.class)
class FeatureFlagRepositoryIT {
    @Test void flagKeyIsUnique() { /* second save with the same key -> DuplicateKeyException */ }
}

@AnalyzeClasses(packages = "com.triphuddle.api.platform")
class ArchitectureTest {
    @ArchTest static final ArchTests rules = ArchTests.in(ArchitectureRules.class);
}
```

The rule that earns its keep:

```java
public static final ArchRule ENDPOINTS_ARE_AUTHORIZED = methods()
        .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
        .and().arePublic()
        .and().areAnnotatedWith(GetMapping.class).or().areAnnotatedWith(PostMapping.class)
        .or().areAnnotatedWith(PutMapping.class).or().areAnnotatedWith(DeleteMapping.class)
        .should().beAnnotatedWith(PreAuthorize.class)
        .because("an endpoint without @PreAuthorize is open to anyone the filter chain lets through");
```

### What api-platform ends up with

| Class | Covered by |
|---|---|
| `CountryService`, `CurrencyService`, `LanguageService` | `*ServiceTest` (every branch), `*ApiIT` (wired) |
| `*Controller` | `*ApiIT` |
| `*AuthorizationPolicy` | `*AuthorizationPolicyTest` (every role), `*ApiIT` (through HTTP) |
| `*Mapper` (generated) | `*MapperTest`, and the service tests through `@Spy` |
| `*Repository` derived queries | `*ApiIT` conflict tests on real Postgres |
| `SecurityConfig` public matchers | `readsArePublicWithoutAToken`, matrix ITs as anonymous |
| `GlobalExceptionHandler` (common) | 400/404/422 cases in the ITs, plus its own unit test in `common` |
| V1, V2 migrations | every IT context (schema), `SeedMigrationIT` (data) |
| `FeatureFlag` | `FeatureFlagRepositoryIT` |

Expected: above 90% lines and 80% branches for api-platform, with about 15 test classes, most of them short.

## Smoke tests: every flow and requirement, end to end

The third layer, after unit and integration tests. Unit tests and ITs prove each service on its own, with
RabbitMQ faked or contained. Smoke tests prove the system: the real services from `docker-compose`, talking through
the real gateway, RabbitMQ, Postgres, Mongo and the notification service. Each one walks a flow or requirement the
READMEs describe, as a client would.

### What they cover

The READMEs already number what the system must do, so the smoke suite can be checked against them:

| Source | IDs | Examples |
|---|---|---|
| Root README, "End-to-end flows" | 5 flows | Joining a group by invitation; Subscribing and paying; Planning a trip; Checking visas; Getting help from support |
| Root README, "Software requirements" | FR-1 to FR-28, NFR-1 to NFR-11 | FR-6 one pending invitation per email and group; NFR-1 every request needs a token |
| Each service's README | 82 IDs across 14 modules | PLT-1 to PLT-4, GRP-1 to GRP-5, DST-1 to DST-10, … |

Each test says which IDs it proves. A traceability check reads every README, collects the IDs, and fails the build
when one has no test or when a test names an ID no README has. So a new requirement written in a README shows up as
a failing build until someone tests it, and a requirement removed from a README takes its stale test with it.

Some requirements aren't observable end to end: NFR-5's `SKIP LOCKED` sharing, or "a failed message is retried
three times". Those are marked `@CoveredBy(SomeIT.class)` with the integration test that proves them, so the
traceability report still accounts for every ID.

### Where they live

A new module, `smoke-tests`, that depends on nothing but `amqp` (for the event classes) and test libraries. It is a
black box: it never imports a service's classes or reads a service's database. It talks HTTP and STOMP to the
gateway, and reads what the outside world would see: Mailpit for emails, a fake push sink, Stripe test mode.

```
smoke-tests/src/test/java/com/triphuddle/smoke/
├── support/
│   ├── SmokeTest.java            # meta-annotation: tag "smoke", stack started, clients injected
│   ├── Stack.java                # starts docker-compose (or points at a running deployment by env var)
│   ├── Actors.java               # creates real users per test: admin, support, customers
│   ├── GatewayClient.java        # REST client through the gateway, one per actor
│   ├── StompClient.java          # real-time events
│   ├── Mailbox.java              # Mailpit's API: await an email, follow its link
│   ├── Requirement.java          # @Requirement({"FR-6", "GRP-2"})
│   ├── Flow.java                 # @Flow("Joining a group by invitation")
│   └── CoveredBy.java            # @CoveredBy(GroupInvitationApiIT.class) for what only an IT can prove
├── flows/                        # one class per root-README flow
│   ├── JoiningAGroupByInvitationSmokeTest.java
│   ├── SubscribingAndPayingSmokeTest.java
│   ├── PlanningATripSmokeTest.java
│   ├── CheckingVisasSmokeTest.java
│   └── GettingHelpFromSupportSmokeTest.java
├── requirements/                 # one class per service, for its own IDs
│   ├── PlatformSmokeTest.java
│   ├── GroupSmokeTest.java
│   └── ...
└── TraceabilityTest.java         # every README ID has a test, every tested ID is in a README
```

### What one looks like

```java
@SmokeTest
@Flow("Joining a group by invitation")
class JoiningAGroupByInvitationSmokeTest {

    @Autowired Actors actors;
    @Autowired Mailbox mailbox;

    @Test
    @Requirement({"FR-5", "FR-6", "FR-7", "FR-2", "GRP-1", "CHT-2", "NTF-1"})
    void anInviteeWhoSignsUpBecomesAMemberAndJoinsTheGroupChat() {
        Actor admin = actors.verifiedUser();
        GroupResponse group = admin.groups().create("Lisbon 2027");

        String email = actors.freshEmail();
        admin.groups().invite(group.getId(), email);

        Email invitation = mailbox.awaitEmail(email, "invited you to");
        Actor invitee = actors.signUp(email);                           // Firebase sign-up, as the app does it
        invitee.followLink(mailbox.awaitEmail(email, "Verify").link()); // FR-2
        invitee.followLink(invitation.link());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(admin.groups().members(group.getId())).extracting("userId").contains(invitee.userId());
            assertThat(invitee.chat().conversations()).extracting("groupId").contains(group.getId());
        });
    }

    @Test
    @Requirement("FR-6")
    void aSecondPendingInvitationForTheSameEmailIsRefused() {
        Actor admin = actors.verifiedUser();
        GroupResponse group = admin.groups().create("Porto");
        String email = actors.freshEmail();
        admin.groups().invite(group.getId(), email);

        assertThat(admin.groups().inviteRaw(group.getId(), email).statusCode()).isEqualTo(422);
    }
}
```

And for api-platform, the requirements that only the running system shows:

```java
@SmokeTest
class PlatformSmokeTest {

    @Test
    @Requirement({"PLT-2", "NFR-1"})
    void listsAreReadableWithoutATokenButChangesNeedAdminOrSupport() {
        assertThat(Anonymous.get("/api/v1/platform/countries?page=1&pageSize=5").statusCode()).isEqualTo(200);
        assertThat(actors.verifiedUser().platform().createCountryRaw(validCountry()).statusCode()).isEqualTo(403);
        assertThat(actors.support().platform().createCountryRaw(validCountry()).statusCode()).isEqualTo(201);
    }

    @Test
    @Requirement("PLT-3")
    void aCountryCreatedHereReachesApiDestination() {
        CountryResponse country = actors.admin().platform().createCountry(validCountry());
        await().untilAsserted(() ->
                assertThat(actors.admin().destination().countries()).extracting("alpha2").contains(country.getAlpha2()));
    }

    @Test
    @CoveredBy({CountryApiIT.class, SeedMigrationIT.class})
    @Requirement({"PLT-1", "PLT-4"})
    void uniquenessIsProvedByTheIntegrationTests() { }
}
```

### Running them

- **Locally:** `mvn -pl smoke-tests verify -Psmoke`. This starts the stack with Testcontainers' `ComposeContainer`
  from `docker-compose.yml` plus a `docker-compose.smoke.yml` override that swaps in the fakes below. Expect minutes,
  not seconds.
- **Against a deployment:** `SMOKE_BASE_URL=https://staging… mvn -pl smoke-tests verify -Psmoke`. The same tests run
  against staging after each deploy, and a red result blocks promotion.
- **In CI:** after unit tests and ITs pass: on pull requests into `live`, `main` and `e2e-testing`, on pushes to
  `main`, nightly, and manually. See CI below.

### What gets faked, and how

| External | In smoke tests |
|---|---|
| Firebase Auth | A separate Firebase test project: `Actors` creates users through the Admin SDK, sets their `role` and `userId` claims, and signs in for real tokens. The services accept that project's issuer only under a `smoke` profile |
| Stripe | Stripe test mode with the Stripe CLI forwarding real signed webhooks, as `docker-compose` already does |
| OpenAI | A fake worker on the same queues that answers with canned JSON, so flows run without cost or flakiness |
| Wikidata, Commons, passport-index | WireMock with recorded responses for one small country |
| Email | Mailpit, which the tests read through its API |
| Push (FCM) | A fake that records what would have been sent |

### Data

Every test creates its own users, groups and resources with unique names, so tests never depend on each other or on
order and can run in parallel. Nothing is cleaned up between tests. The stack is thrown away at the end, and against
staging the data is tagged `smoke-` so a job can sweep it.

## Rollout

1. **`test-support`, `common` and `amqp`:** the build plugins, the `test-support` module, and `common`'s own tests
   (`BaseAuthorizationPolicy`, `FirebaseJwtGrantedAuthoritiesConverter`, `PaginationFilter`, `GlobalExceptionHandler`,
   `CursorPaginationFilter`).
2. **api-platform** as the pilot, exactly as above. We review it together before going wider.
3. **api-group, api-user, api-license:** CRUD-heavy, so they lean on the generic bases most.
4. **api-payment:** adds Stripe. Stripe is faked at the `stripe` module's client interface, and webhooks are tested
   with signed payloads.
5. **api-chat:** WebSocket/STOMP and Mongo messages.
6. **api-destination:** PostGIS and pgvector search, the AI worker over AMQP, Wikidata/Commons over HTTP (WireMock).
7. **notification, openai, stripe, api-gateway:** smaller suites, mostly unit tests and HTTP fakes.
8. **smoke-tests:** the module, the traceability check and the fakes first, then one flow at a time in the root
   README's order. Until every ID has a test, the traceability check reports the missing IDs without failing the build.

Each step lands with its coverage gate on, so no module goes back below the bar once it reaches it.

## Where it stands

Steps 1 to 7 of the rollout are done, and `mvn verify` runs 1,863 tests in about three and a half minutes on a laptop with images cached. Every module with code has its gate on:

| Module | Lines | Branches |
|---|---|---|
| common | 99.8% | 96.9% |
| amqp | 100% | 100% |
| stripe | 100% | 97.6% |
| api-platform | 100% | 100% |
| api-group | 100% | 95.4% |
| api-user | 100% | 96.4% |
| api-license | 99.9% | 93.8% |
| api-payment | 99.9% | 90.1% |
| api-chat | 98.7% | 89.3% |
| api-destination | 98.8% | 85.8% |
| notification | 93.8% | 94.8% |
| openai | 100% | 93.0% |
| api-gateway | 100% | 100% |

What the tests still leave out, and why:

- **Clients that need real Google credentials:** `FirebaseMessagingConfig` in notification and the `StorageConfig` beans in api-chat and api-destination only hand Application Default Credentials to Google's SDK. The code that uses them is tested against mocks.
- **Guards the API can't reach today:**
  - a system message can't be reported, but system messages exist only in support chats, which refuse reports first;
  - "claim the ticket before working on it", as reopening an unassigned ticket goes back to OPEN;
  - a question over the configured length, as the request's own validation has the same limit;
  - editing someone else's message, which the policy refuses first.

  They stay in case the rules around them change.
- **Races:** a reaction to a message deleted between the check and the update. The duplicate send and the used-up promotion races are unit-tested.
- **Failures that can't happen:** SHA-256 missing from the JVM, unreadable JSON the service stored itself, and `null` from a SQL `count(*)`.
- **Short-circuit leftovers:** branches inside conditions that are already decided by an earlier part, such as filters re-checking what the query selected.

eureka-server has one integration test and no gate, as its only class is the application.

Step 8 has its frame: the `smoke-tests` module, the traceability check, the stack script and fakes, `smoke-testing.yml` and `mutation-testing.yml`. Traceability counts 121 requirements: 94 have a test (85 through integration tests, the rest through smoke tests), and 27 are left, almost all `FR-` IDs waiting for their flow. One of the five flows, "Getting help from support", has its smoke test. None has run yet: they need the Firebase test project and its secrets.

Where the plan changed:

- **api-gateway's backends** are a plain JDK HTTP server, not WireMock, which wasn't available offline. The tests are the same.
- **The smoke stack** is the infrastructure from `docker-compose.yml` plus each service's jar, started by `scripts/smoke-stack.sh`. The compose file has no services to start.
- **Messaging ITs** set the broker through `@DynamicPropertySource`, as amqp's own does. The shared config's connection factory ignores `@ServiceConnection`.
- **Workers without a database** (notification, openai) exclude the JDBC auto-configuration in their `it` profile, as test-support brings JPA onto the test classpath.

Found and fixed while writing the tests, beyond what "Found while planning" lists:

- api-chat: the queue's assignee filter failed on a null UUID; review, report and block responses had no `createdAt`.
- api-destination: Wikidata's importer threw on every notable place without a population, a ternary unboxing a null; a visa import queued after a dead one always failed, as Hibernate inserted before it updated; plan, itinerary, review, mark, report and import responses had no `createdAt`.
- notification: failures that can never pass were retried three times before dead-lettering, though the code meant to dead-letter them at once.
- api-gateway: there was no `/fallback`, so a service that was down answered 404; `/actuator/health` wasn't exposed; any other actuator endpoint is now refused.
- Every service publishes RabbitMQ events after commit through `RabbitMQMessageProducer.publishAfterCommit`; api-chat and api-destination dropped their own copies.

## Found while planning

The tests above would fail today on these:

- `CountryService.getEntityById` uses `findById`, so a deleted country can still be read, updated, disabled and
  deleted again (each time publishing another `CountryDeletedEvent`). `getAll` lists deleted ones too. The same holds
  for currencies and languages.
- Events are published inside the transaction. If the save then fails at commit, the event has already gone out.
  api-license already publishes after commit, and NFR-4 in the root README says every service does.

## CI (GitHub Actions)

Built on the two workflows already in `.github/workflows`.

**`unit-integration-testing.yml`**: same triggers as today (pull requests and pushes on `main`, `e2e-testing` and
`live`, nightly, manual). It changes from `mvn test` to `mvn verify`, so integration tests and the JaCoCo gate run.
`ubuntu-latest` has Docker for Testcontainers. It keeps `-Dfirebase.enabled=false`, which `common` and
`notification` already honour, and uploads the Surefire, Failsafe and JaCoCo reports as artifacts.

**`smoke-testing.yml`** (new):

| Trigger | Runs against |
|---|---|
| Pull requests into `live`, `main` and `e2e-testing` | the docker-compose stack built from the PR |
| Pushes to `main` and nightly | the docker-compose stack |
| Manual (`workflow_dispatch`) with an optional `base-url` input | the stack, or the given deployment (staging, once it exists) |

It needs `needs: test`-style ordering only within a run: it starts after the unit and integration job passes. Its
secrets: the Firebase test project's service-account JSON, the Stripe test secret key, and the Stripe CLI's
webhook secret. When staging exists, its deploy triggers this workflow with the staging URL, and a red run blocks
promotion.

**`mutation-testing.yml`** (new): nightly PIT on the `service` and `security` packages of each module, report only.

**`push-live-to-branches.yml`**: merges with a GitHub App installation token instead of `GITHUB_TOKEN`. GitHub starts
no workflows for pushes made with `GITHUB_TOKEN`, so today `main` and `e2e-testing` never run their tests after a
merge. Setting it up takes a GitHub App with `contents: write` on the repository, installed there, with its app ID and
private key as repository secrets (`actions/create-github-app-token` turns them into a token).

## Decisions

| Topic | Decision |
|---|---|
| Shared test code | A new `test-support` module |
| RabbitMQ in ITs | `RecordingMessageProducer` in API ITs; a real RabbitMQ container only in messaging ITs |
| Deleted resources | 404 on every operation and absent from lists; the api-platform fix lands with the pilot |
| Events | Published after commit in every service (NFR-4); tests check a rolled-back change publishes nothing. api-platform is fixed with the pilot |
| Coverage gate | 85% lines, 75% branches per module, unit and integration coverage combined. Reported while a module's suite is being written, enforced once it is done |
| Mutation testing | PIT nightly on `service` and `security` packages, report only |
| Event contracts | A JSON round-trip test per event class in `amqp`, plus consumers' messaging ITs with real messages |
| Test data | `@Builder(toBuilder = true)` on request DTOs; fixtures hold valid defaults |
| Style | AssertJ; Mockito BDD (`given`/`then`); sentence method names; `@Nested` per method under test |
| api-gateway | ITs with WireMock standing in for the services: routing, token rejection, public routes, headers |
| Local runs | `mvn test` is unit-only and needs no Docker; `mvn verify` runs everything and needs Docker |
| CI | GitHub Actions, as above |
| Smoke-test sign-in | A separate Firebase test project with real tokens; CI holds its service-account credential |
| Stripe in smoke tests | Test mode through the Stripe CLI, with real signed webhooks; CI holds a test secret key |
| Smoke-test runs | Pull requests into all three branches, pushes to `main`, nightly, and manually against any base URL |
| Staging | None yet; the manual run takes a base URL, and data created there is tagged `smoke-` for sweeping |
| Requirement IDs | Stay in the READMEs; the traceability check parses all of them |
