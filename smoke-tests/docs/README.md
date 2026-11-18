# smoke-tests

End-to-end tests of the running system. They talk to the gateway as a client would, read the emails it sends, and never import a service's code or open its database. They also check that every requirement in the READMEs has a test.

## In short

- `mvn test` runs only `TraceabilityTest`. It needs no Docker and no running system.
- `mvn verify -Psmoke` runs the smoke tests against a running stack.
- Each smoke test names the requirements it proves with `@Requirement`. A class that walks one of the root README's end-to-end flows names it with `@Flow`.
- `IntegrationCoverage` lists the requirements that integration tests prove better than a client could: retries, locks, uniqueness in the database. Each entry names the integration tests that prove it, with `@CoveredBy("module:ClassName")`.

## Traceability

`TraceabilityTest` reads every README and collects the requirement IDs: `FR-` and `NFR-` in the root, and each module's own IDs (`PLT-`, `CHT-`, `DST-` and the rest). It also reads the root README's flows. It fails when:

- a test names an ID or a flow no README has;
- a `@CoveredBy` names a test class that doesn't exist in that module.

It writes `target/traceability.md`: every ID, its README, and what proves it. IDs and flows without a test are listed there. They fail the build only with `-Dtraceability.strict=true`, so the suite can grow one flow at a time. Turn strict on once it's complete.

## Running against a local stack

`scripts/smoke-stack.sh start` starts, on this machine:

- the infrastructure from `docker-compose.yml` (Postgres, MongoDB, RabbitMQ, Zipkin) and Mailpit;
- the fake OpenAI in `fakes/fake_openai.py`;
- every service's jar, with environment overrides that replace compose's host names with `localhost`, point emails at Mailpit and AI at the fake, and accept only the Firebase test project's tokens.

`scripts/smoke-stack.sh stop` stops it all and drops the data.

```bash
mvn -DskipTests package
```

```bash
SMOKE_FIREBASE_PROJECT_ID=... GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json scripts/smoke-stack.sh start
```

```bash
SMOKE_FIREBASE_API_KEY=... GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json mvn -pl smoke-tests verify -Psmoke
```

| Setting | What it is |
|---|---|
| `SMOKE_FIREBASE_PROJECT_ID` | A Firebase project for tests only. Services accept its tokens and no others. |
| `SMOKE_FIREBASE_API_KEY` | Its web API key. Tests sign users up and in through Firebase's REST API, as the app does. |
| `GOOGLE_APPLICATION_CREDENTIALS` | Its service-account key. api-user sets claims with it, and the tests give staff their role. |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | Test mode, for the payment flow. Without them api-payment isn't started. |
| `SMOKE_BASE_URL` | The gateway. The default is `http://localhost:8080`; set it to test a deployment. |
| `SMOKE_MAILPIT_URL` | Mailpit's API. The default is `http://localhost:8025`. |

## What is faked

| Outside | In smoke runs |
|---|---|
| Firebase Auth | The test project, with real tokens |
| Email | Mailpit, read through its API |
| OpenAI | `fake_openai.py`: canned answers in the shape each job's schema asks for, and vectors derived from the text |
| Stripe | Test mode, with the Stripe CLI forwarding signed webhooks |
| FCM | Off; pushes are dropped. `notification`'s integration tests cover them |
| Wikidata, Commons, the visa dataset | Not called. Tests create places directly, and imports are proved by api-destination's integration tests |

## Data

Each test creates its own users and data, with emails starting with `smoke-`, so tests don't depend on each other. Countries use codes ISO leaves to users. On a shared deployment, codes from deleted countries stay taken; sweep that data now and then.

## In CI

`smoke-testing.yml` runs on pull requests into `main`, `e2e-testing` and `live`, on pushes to `main`, nightly, and on demand. A run on demand can take a `base-url` to test a deployment instead of starting the stack. The unit and integration tests run first, and the smoke tests start only if they pass. Without the Firebase secrets (`SMOKE_FIREBASE_PROJECT_ID`, `SMOKE_FIREBASE_API_KEY`, `SMOKE_FIREBASE_CREDENTIALS`), for example on a fork's pull request, the job says so and skips. `SMOKE_STRIPE_SECRET_KEY` adds the payment flow.
