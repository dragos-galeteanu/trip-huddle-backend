# api-destination

Places, their category tree, tags and photos, imported from Wikidata and Wikimedia Commons, AI content about them, trip plans with AI itineraries, and visa guidance for each traveller, all written through the openai worker, on port **8087**, in PostgreSQL with PostGIS, pgvector, pg_trgm and unaccent (`jobbie_giggle_destination`). Travellers also keep favourites and visited places, write reviews, ask about places, and find places by meaning.

## In short

The heart of the product: where to go and how. It holds the world's places, from countries down to landmarks and restaurants, imported from Wikidata with photos from Wikimedia Commons. It has the AI write about each place, finds places by words, distance or meaning, and plans trips with day-by-day itineraries. It tells each traveller what visa they need, and keeps people's favourites, reviews and questions. Everything needs a license.

**Stack:** Spring Boot (Web, Data JPA, Security), PostgreSQL 16 with PostGIS (geography), pg_trgm and unaccent (fuzzy text search) and pgvector (semantic search), RabbitMQ, Google Cloud Storage (photo copies). AI goes through the [openai](../../openai/docs/README.md) worker over amqp, and open data comes from Wikidata SPARQL, Wikimedia Commons and the passport-index dataset.

## User stories

**Exploring**
- As a traveller, I browse a country's regions, cities and landmarks, filter them by category or tag, and search by name in any language, forgiving typos.
- As a traveller, I find places near me or on the visible map.
- As a traveller, I describe what I'm after ("quiet beach town with seafood"), or ask for places like one I liked.
- As a traveller, I read a place's overview, pros and cons, what visitors say with the sources, and practical tips, in my language.
- As a traveller, I ask my own question about a place and get a sourced answer.

**Planning**
- As a traveller or group member, I make a trip plan with dates, destinations, must-sees, travellers, budget, pace and interests.
- As a traveller, I get an AI itinerary for it, and I'm told by push, email and in the app when it's ready.
- As a traveller, I have one day rewritten, or reorder, add and remove items myself.

**Visas**
- As a traveller, I see for each country on my trip what my best passport needs, with steps, costs and official links.
- As a group admin, I see every member's visa situation, and who hasn't entered their documents yet.

**My lists**
- As a traveller, I save favourites, mark places visited, and write one review per place.
- As a traveller, I report reviews or AI content that are wrong.

**Staff**
- As an administrator, I import a country from Wikidata, then fix, move, merge, hide or feature places and manage the category tree.
- As a support agent, I settle reports on content and reviews, and have content rewritten.
- As an administrator, I reload the visa rules.

## Main flows

The [root README](../../README.md#end-to-end-flows) shows planning a trip and checking visas end to end. Here:
- **AI content:** see [AI content](#ai-content).
- **Itineraries:** see [Trips and itineraries](#trips-and-itineraries).
- **Visas:** see [Visas](#visas).
- **Semantic search:** see [Semantic search](#semantic-search).

## Requirements

- **DST-1** Every route needs a token and a current license with the access entitlement, the user's own or, when acting for a group, the group's. Access lasts 48 hours past a period's end, for a renewal on its way. Staff always have access.
- **DST-2** Places form a tree under each country. Slugs, Wikidata IDs and OSM IDs are unique. Merged places answer with their target, and hidden ones are for staff only.
- **DST-3** Imports load regions, cities above a population threshold, and notable places with photos whose licences allow reuse. Wikimedia's limits are respected with pacing and retries.
- **DST-4** Text search matches every name in any language, ignoring accents and forgiving typos. Points rank by distance; otherwise featured and populous places come first.
- **DST-5** AI content is written once per place, section and language, and shared. It is served while stale and refreshed, within a daily token budget, a hard limit and a per-user share.
- **DST-6** Trip plans run up to 30 days and 10 destinations. An itinerary takes one unit of the owner's monthly quota, and a failure gives it back. Only known, listed places are linked. Travellers are told by socket, push and email.
- **DST-7** Visa checks use each traveller's best passport, and residence permits valid through the trip. Guidance is shared by everyone in the same case and carries a disclaimer. Group admins see everyone, and members only themselves.
- **DST-8** Favourites, visited places and questions are private. There is one review per user and place, and three open reports hide a review until staff decide.
- **DST-9** Semantic search embeds places in the background and caches each query's embedding, so repeated searches are instant.
- **DST-10** A deleted user's documents, marks, reviews and questions are forgotten.

---

## Technical reference

The sections below describe how it works.

## Access

Every route needs a token and a license:
- **Staff:** Admin, Support and Inspector always read; Admin and Support manage places and categories.
- **Everyone else:** needs a current subscription whose entitlements include `app.destination.access-entitlement` (`DESTINATIONS`). A value of `false`, `0`, `no` or `off` turns it off.
- **Groups:** a group's license counts only for its members, and only when they pass `groupId`.
- **Grace:** access lasts `access-grace-hours` (48) past a period's end, for a renewal on its way.

Copies kept from events: countries (api-platform), license subscriptions with entitlements (api-license), and group memberships (api-group, `.destination` queues). They only know what happened after this service started listening, so existing countries, subscriptions and memberships need a replay.

## Endpoints

| Endpoint | Who |
|---|---|
| `GET /api/v1/destinations/places?q=&countryCode=&kind=&categoryId=&tag=&withinId=&parentId=&isFeatured=&latitude=&longitude=&radiusKm=&west=&south=&east=&north=&groupId=` | licensed users, staff |
| `GET /api/v1/destinations/places/{id}`, `/slug/{slug}` | licensed users, staff; a merged place answers with its target |
| `POST /places`, `PUT /places/{id}`, `POST /places/{id}/move`, `/merge`, `/hide`, `/unhide`, `/feature`, `/unfeature`, `DELETE /places/{id}` | Admin, Support |
| `POST /places/{id}/photos/{photoId}/primary`, `DELETE /places/{id}/photos/{photoId}` | Admin, Support |
| `GET /api/v1/destinations/places/{id}/content?language=&sections=&groupId=` | licensed users, staff; `language` or else `Accept-Language`, else English |
| `POST /places/{id}/content/{section}/report {language, reason}` | licensed users |
| `POST /places/{id}/content/regenerate {language, sections}` | Admin, Support; answers `202` |
| `GET /api/v1/destinations/content-reports?isOpen=&cursor=&limit=`, `POST /content-reports/{id}/resolve?isRegenerating=` | staff read; Admin, Support resolve |
| `POST /api/v1/destinations/imports {countryCode, minCityPopulation?, minPoiSitelinks?, isIncludingPois?, isIncludingPhotos?}` | Admin, Support; answers `202` with the queued import |
| `GET /api/v1/destinations/imports?countryCode=&status=`, `GET /imports/{id}` | Admin, Support, Inspector |
| `GET /api/v1/destinations/places/semantic?q=&countryCode=&kind=&withinId=&limit=&groupId=` | licensed users, staff |
| `GET /places/{id}/similar?isSameCountry=&limit=&groupId=` | licensed users, staff |
| `PUT`, `DELETE /places/{id}/favourite`; `PUT /places/{id}/visited {visitedOn?}`, `DELETE /places/{id}/visited` | licensed users (removing: any signed-in user) |
| `GET /me/places?kind=FAVOURITE\|VISITED&cursor=&limit=`, `GET /me/place-marks?placeIds=` | the user |
| `GET /places/{id}/reviews?sort=NEWEST\|HIGHEST\|LOWEST&cursor=&limit=`, `GET /places/{id}/reviews/summary` | licensed users, staff |
| `GET`, `PUT`, `DELETE /places/{id}/reviews/mine` | the author (writing needs the license) |
| `POST /reviews/{id}/report {reason}` | licensed users, not the author |
| `POST /reviews/{id}/moderate?isHiding=&reason=`, `POST /review-reports/{id}/resolve?isHiding=` | Admin, Support |
| `GET /review-reports?isOpen=&cursor=&limit=` | staff |
| `POST /places/{id}/questions {question, language}` | licensed users; answers `202` |
| `GET /places/{id}/questions/mine?cursor=&limit=`, `GET /questions/{id}` | the asker; staff read any |
| `GET /api/v1/destinations/categories` | licensed users, staff |
| `POST`, `PUT`, `DELETE /api/v1/destinations/categories` | Admin, Support |
| `POST /api/v1/destinations/trip-plans` | licensed users; with `groupId`, members of a licensed group |
| `GET /trip-plans/mine`, `GET /trip-plans/groups/{groupId}` | the user; the group's licensed members, staff |
| `GET`, `PUT /trip-plans/{id}` | its travellers; staff read |
| `DELETE /trip-plans/{id}` | its creator, or an admin of its group |
| `POST /trip-plans/{id}/itineraries` | its travellers; answers `202` with the itinerary `GENERATING` |
| `GET /trip-plans/{id}/itineraries`, `GET /itineraries/{id}` | its travellers, staff |
| `POST /itineraries/{id}/days/{n}/regenerate?instructions=` | its travellers; answers `202` |
| `GET /trip-plans/{id}/visas?language=` | its travellers (a group's admins see everyone, members themselves), staff |
| `GET /api/v1/destinations/visas?passport=&destination=&language=&groupId=` | licensed users, staff |
| `POST /api/v1/destinations/visa-rules/imports` | Admin, Support; answers `202` |
| `GET /visa-rules/imports` | staff |
| `PUT /itineraries/{id}/days/{n}/order [itemIds]`, `POST /itineraries/{id}/days/{n}/items`, `DELETE /itineraries/{id}/items/{itemId}` | its travellers |

Search matches every name a place has, in any language, ignoring accents and forgiving typos (trigram plus full text). Words rank by match, a point ranks by distance, otherwise featured and most populous come first.

Favourites and visited places, a place's reviews, a user's questions, and the content and review reports use **cursor pagination** (`cursor`, `limit`; the answer has `nextCursor` and `hasMore`): feeds that grow at their head, and report queues that shrink as staff work through them, would make page numbers repeat or skip items. A review cursor belongs to its sort; one from another sort is refused. Other lists keep `page` and `pageSize`.

## Staff operations

- **Create:** needs an active country from api-platform's events, and a parent in the same country if given. A slug like `pt-porto` is made from the country and name unless given; slugs are never reused.
- **Names:** names, categories and tags are replaced as sent. The primary name is always among the names, so search finds it.
- **Wikidata and OpenStreetMap IDs** are unique: a second place with the same one is refused, to be merged instead.
- **Move:** moves a place with its whole subtree, within its country. Moving it into itself or into a place inside it is refused.
- **Merge:** a duplicate's children, names (as aliases), categories, tags and source IDs go to the target, and the duplicate becomes `MERGED`. Reading the duplicate answers with the target. Places in different countries, or a target inside the duplicate, are refused.
- **Hide:** hides a place from customers; its children stay listed. Staff can still read it and include it in searches with `isIncludingHidden`.
- **Delete:** only places with nothing in them; others are moved or merged first.
- **Categories:** a slug is unique among categories that aren't deleted. A new parent moves a category with its subtree, and deleting one with subcategories is refused. Places lose a deleted category.

## Importing from Wikidata

Staff queue an import per country; one country has at most one waiting or running import. `ImportJobRunner` takes waiting imports every `interval-ms` (30s), one per instance at a time. An import whose instance stops renewing its heartbeat for 15 minutes is taken up again by another instance.

1. **Country:** found by its ISO code (`P297`). Several historical states can share a code; the one with the most articles is taken. Its official languages' names are imported along with the configured `languages`.
2. **Regions:** the country's first-level administrative subdivisions (`Q10864048` and subclasses) that weren't dissolved, at the top of the country.
3. **Cities:** human settlements (`Q486972` and subclasses) with at least `minCityPopulation` (20,000) people, under the region they lie in (`P131`).
4. **Places to visit:** for each category with Wikidata classes, e.g. Castle with `Q23413`:
   - the `max-pois-per-class` (300) most notable items of that class and its subclasses, with at least `minPoiSitelinks` (5) Wikipedia articles and coordinates;
   - each is filed under that category, in the nearest city within `poi-city-radius-km` (25 km).
5. **Photos:** each place's Wikidata image (`P18`).

**Names:** a place's name is its English label, else its country's language, else any. Every label in the chosen languages becomes one of its names.

**Re-runs:** a re-run matches places by Wikidata ID, so it updates rather than duplicates.
- It sets coordinates when missing, the population, the OpenStreetMap relation (`P402`, as `relation/<id>`) when free, new names, and the category.
- It leaves the name, kind, parent, slug and visibility as staff set them, and never brings back a deleted place.

**Progress and failure:** progress, counts and the error of a `FAILED` import are on the import. What was imported before a failure stays.

**Wikimedia's limits:**
- **Pacing and retries:** queries go one at a time, at least `query-interval-ms` (2s) apart. A `429` or `5xx` is retried up to `max-attempts` (4), honouring `Retry-After` up to `max-retry-wait-seconds`.
- **User-Agent:** Wikimedia asks automated clients to send one naming the operator and a way to reach them. Set `DESTINATION_IMPORT_USER_AGENT`, e.g. `TripHuddleBot/1.0 (https://your.site; ops@your.site)`.
- **Query safety:** only Q-numbers are put into SPARQL, so nothing else can be injected.

## Photos

Images come from Wikimedia Commons, freely licensed only: CC0, public domain, CC BY, CC BY-SA, GFDL, FAL. Each photo keeps its author (as plain text), licence and its link, and the file's Commons page, and responses carry them. The frontend must show the author and licence when `isAttributionRequired`.

- **Bucket:** with `DESTINATION_PHOTOS_BUCKET` set, a copy at `photo-width` (1600 px) is stored at `places/<placeId>/<id>.<ext>` and served by signed URL (`download-url-ttl-minutes`, 6 h). Without a bucket, photos are shown from Commons, which allows that.
- **Primary photo:** a place's first photo is its primary one, shown in search results; staff can pick another.
- **Removed photos:** a photo staff remove is never added back by an import.

## AI content

Each place has three sections, each generated in each language on its own and shared by every reader:

| Section | Task | Content (strict JSON schema) | Web search | Fresh for |
|---|---|---|---|---|
| `OVERVIEW` | `place-overview` | `summary`, `highlights`, `pros`, `cons`, `goodFor`, `notIdealFor` | no | 180 days |
| `OPINIONS` | `place-opinions` | `summary`, `praise`, `complaints`, `sentiment` (positive, mixed, negative), plus the cited pages | yes | 30 days |
| `PRACTICAL` | `place-practical` | `bestTimeToVisit`, `suggestedDuration`, `costs`, `gettingThere`, `gettingAround`, `safety`, `customs`, `tips` | no | 90 days |

**Prompts:** they give the model the place's facts, so it writes about the right one rather than a namesake: its name, kind, country, breadcrumb, categories, coordinates, population and Wikidata ID. The model is told to write in the reader's language, and not to invent hours, prices or names.

**Each section comes back in one of these states:**
- **`READY`:** with the content, `isAiGenerated` (the frontend shows the label), the model and the citations.
- **`GENERATING`:** the reader asks again shortly. While another language is written, the English version stands in with `isFallback`.
- **`FAILED`:** the last try failed; a read tries again after `retry-failed-after-minutes` (60).
- **`UNAVAILABLE`:** the day's budget or the reader's share is spent.

**Life of a section:**
- **First read:** sends it to the openai worker (`AiTextRequestedEvent`, once the transaction commits) and marks it pending, so other readers don't send it again.
- **Answers:** arrive on `ai.text.completed.destination.queue`. A good one becomes the new version. A failed one leaves the last version in place, to be tried again later. An answer to a job no longer waited for is ignored, but its tokens still count.
- **Aged content:** still served, with `isRefreshing`, while a new version is written. `ContentJobs` (every 10 minutes) refreshes aged sections read in the last `refresh-viewed-within-days` (30) ahead of time; the rest wait for their next read. It also sends again jobs whose answer is over `pending-timeout-minutes` (30) late.

**Cost:**
- **Token budget:** tokens are counted per UTC day. Refreshes and staff requests stop at `daily-token-budget`; generation for readers stops at `daily-token-hard-limit`.
- **Per reader:** each reader may start `max-generations-per-user-per-day` (30) generations. Reading what exists costs nothing.

**Reports:** readers report a section; staff see open reports and keep it or regenerate it.

## Trips and itineraries

**Plans:** a trip plan belongs to a user, or to a group when created with `groupId`. Its travellers are that user, or the group's members: the group's license counts within its plans, and a member's own license doesn't open another group's.
- **What a plan holds:** title, dates (at most `max-trip-days`, 30), adults and children, budget (`BUDGET`, `MODERATE`, `LUXURY`), pace (`RELAXED`, `BALANCED`, `PACKED`), interests, notes and language.
- **Places:** up to `max-places` (10) destinations, plus must-sees; every one must be listed.

**Writing an itinerary:**
- **The job:** `POST .../itineraries` sends the `itinerary` task (a strong model, strict schema) once its transaction commits.
- **What the model gets:** the plan, its destinations, and up to `candidates-per-place` (25) of the best known places within each, with their IDs. Each item may link to one of them.
- **Checking the answer:** a place ID the model made up, or one that is no longer listed, is dropped from its item. Days past the trip's end are dropped.
- **One at a time:** a plan has at most one itinerary `GENERATING`.

**Quota:**
- **Monthly limit:** each itinerary takes one unit of the owner's monthly quota, the largest `AI_ITINERARIES_PER_MONTH` among its current subscriptions (a number, or `unlimited`; none means no itineraries).
- **Group plans:** they count against the group, whoever asks.
- **Failures:** a failed itinerary, or one whose answer is over `pending-timeout-minutes` (30) late, gives its unit back.
- **Token limits:** the day's token hard limit and the user's generation share apply as for content.

**Afterwards:**
- **Rewriting a day:** travellers rewrite one day (`itinerary-day`, optionally with instructions such as "more food, less walking"). Day rewrites are free of quota. A failed or overdue rewrite leaves the day as it was.
- **Editing:** travellers reorder, add and remove items by hand.

**Delivery:** when an itinerary is `READY` or `FAILED`, this service publishes `ItineraryFinishedEvent` to `destination.itinerary.finished.exchange`, and a push to whoever asked. Then:
- **api-chat** sends `ITINERARY_FINISHED` to every traveller's socket;
- **api-user** emails the requester `ITINERARY_READY` with a link to `plan-url` (READY only);
- **polling:** clients without these can poll `GET /itineraries/{id}`.

## Visas

**Rules:** one row per passport and destination, from the [passport-index dataset](https://github.com/ilyankou/passport-index-dataset) (MIT).
- **Requirements:** `CITIZEN`, `VISA_FREE` (with days when known), `VISA_ON_ARRIVAL`, `ETA`, `E_VISA`, `VISA_REQUIRED`, `NO_ADMISSION`, from least to most effort.
- **Loading:** staff queue an import, and `ImportJobRunner` downloads `visas.dataset-url` and upserts it. One import runs at a time. Rows with values it doesn't know (the old `covid ban`) are skipped and counted.
- **Removing:** a download of at least `min-rules-for-full-import` (30,000) rows also removes the pairs the dataset dropped. A smaller one removes nothing, as it may have been cut short.
- **Freshness:** the original repository was archived in January 2025, so point `DESTINATION_VISA_DATASET_URL` at a maintained fork in the same `Passport,Destination,Requirement` ISO2 format.

**Travellers' documents:** nationalities and residence permits come from api-user (`UserTravelDocumentsUpdatedEvent`). The latest `changedAt` wins. On `UserDeletedEvent` they are emptied, and the row stays so a late update can't bring them back. No document numbers are kept.

**A trip's checks:**
- **Who and where:** for each traveller, and each country the plan's places are in.
- **The rule:** the traveller's easiest passport, which is also shown against each of their nationalities.
- **Permits:** residence permits count only if valid until the trip ends.
- **No guidance needed:** a citizen's check is `NOT_NEEDED`, and a traveller without a nationality is `isMissingDocuments`.
- **Who sees whom:** a group's admins and staff see every traveller with their documents; members see only themselves.

**Guidance:**
- **The job:** the `visa-guidance` task (a strong model, with web search) explains the case. It is given the passport, destination, the dataset's rule and the permit countries.
- **What comes back:** steps, documents, costs, processing time, stay limit, what the permits change, official links, and warnings where official sources differ from the dataset.
- **Shared:** guidance is keyed by destination, passport, requirement, permit countries and language, so it holds nothing about any one traveller and is written once for everyone in the same case.
- **Lifecycle and limits:** like content: `GENERATING` until answered, fresh for `guidance-fresh-days` (30), retried after `retry-failed-after-minutes` (60), given up on after `pending-timeout-minutes` (30). The daily token hard limit and each user's generation share apply.
- **Disclaimer:** every response carries one, saying the guidance is AI-written and not legal advice.

## Favourites, visited places and reviews

**Marks:** a user's favourites and visited places (with an optional visit date) are theirs alone. Marking twice is harmless. A merged place's mark goes on the place it went into. Hidden and deleted places drop out of the lists while they are hidden.

**Reviews:**
- **One each:** one review per user and place, rated 1 to 5, with an optional title, text and visit date. Writing again rewrites it; deleting it frees the place for a new one.
- **Summary:** count, average to one decimal, and how many give each rating, all over the visible reviews.
- **Reports:** users report others' reviews, once each. At `review-auto-hide-reports` (3) open reports the review is hidden until staff settle the reports, keeping or hiding it. Staff can also hide or show any review.
- **Hidden reviews:** shown only to their author (with the reason) and staff. An author's edit doesn't unhide one.

## Questions about a place

- **Asking:** `POST .../questions` sends the `place-question` task (a strong model, with web search), with the place's facts and the question. Questions are limited to `max-question-length` (1,000).
- **The answer:** `answer`, `tips`, `caveats` and the cited pages. The model is told to stay on the trip.
- **Private and limited:** each question and answer is the asker's alone. The daily token hard limit and each user's generation share apply. An answer over `question-pending-timeout-minutes` (30) late fails the question.

## Semantic search

- **Place embeddings:** `ContentJobs` (every 10 minutes) sends a batch of up to `embedding-batch-size` (200) listed places to the openai worker (`place-embedding`) while the day's bulk budget lasts. Each place is described in English: name, kind, breadcrumb, other names, categories, tags, and its English overview's summary, highlights and "good for". Vectors are stored in pgvector (`vector(1536)`, HNSW cosine index).
- **Keeping them fresh:** a place is checked again when it or its English overview changes. If the text's hash is unchanged it isn't sent. A vector of the wrong length, or a failed or overdue job, leaves the place to be sent again.
- **Queries:** `GET /places/semantic?q=` normalises the query, then embeds it once (`query-embedding`) and caches it. A new query answers `GENERATING`, so ask again in a moment; after that the same query is answered from the cache.
- **Results:** listed places, nearest in meaning first, with a similarity from 0 to 1. They can be narrowed to a country, kind or subtree. With narrow filters, the index's approximate scan may return fewer than `limit`.
- **Similar places:** `GET /places/{id}/similar` uses the place's own vector, optionally within its country, and answers `GENERATING` until the place is embedded.
- **Dimensions:** `embedding-dimensions` must match the column; changing the embedding model to one with another length means changing the column and embedding again.

## Messages

| | Message | Queue |
|---|---|---|
| consumes | `CountryCreatedEvent`, `CountryDisabledEvent`, `CountryDeletedEvent` | api-platform's country queues, which nobody else reads |
| consumes | `LicenseSubscriptionActivatedEvent`, `LicenseSubscriptionEndedEvent` | their `.destination` queues, bound to the fanout exchanges |
| consumes | `LicenseSubscriptionRenewedEvent`, `LicenseEntitlementsChangedEvent` | their own queues, which nobody else reads |
| consumes | `GroupMembership*`, `GroupDisabledEvent`, `GroupDeletedEvent` | their `.destination` queues |
| publishes | `AiTextRequestedEvent` | `ai.text.requested.queue`, once its transaction commits |
| consumes | `AiTextCompletedEvent` | `ai.text.completed.destination.queue`; tasks `itinerary*` go to itineraries, `visa-guidance` to visas, `place-question` to questions, the rest to content |
| consumes | `UserTravelDocumentsUpdatedEvent` | `user.travel.documents.updated.queue` |
| consumes | `UserDeletedEvent` | `user.deleted.destination.queue`, bound to the fanout exchange; forgets the user's documents, marks, reviews and questions |
| publishes | `AiEmbeddingRequestedEvent` | `ai.embedding.requested.queue`, once its transaction commits |
| consumes | `AiEmbeddingCompletedEvent` | `ai.embedding.completed.destination.queue` |
| publishes | `ItineraryFinishedEvent` | `destination.itinerary.finished.exchange` (api-chat, api-user), once its transaction commits |
| publishes | `SendPushRequest` | `notification.push.queue`, to whoever asked for the itinerary |

An end that arrives before its activation is kept, so the activation can't reopen access.

## Settings

| Property | Env | Default | Meaning |
|---|---|---|---|
| `app.destination.access-entitlement` | `DESTINATION_ACCESS_ENTITLEMENT` | `DESTINATIONS` | the entitlement that opens the service |
| `app.destination.access-grace-hours` | `DESTINATION_ACCESS_GRACE_HOURS` | 48 | access past a period's end |
| `app.destination.max-radius-km` | | 500 | the largest radius a search may ask for |
| `app.destination.photos.bucket` | `DESTINATION_PHOTOS_BUCKET` | empty | bucket for copies of photos; empty shows them from Commons |
| `app.destination.import.user-agent` | `DESTINATION_IMPORT_USER_AGENT` | a placeholder | sent to Wikimedia; set it to name you and a contact |
| `app.destination.import.languages` | | en, de, fr, es, it, pt, ro, nl, pl | names imported, besides each country's own languages |
| `app.destination.import.*` | | see `application.yml` | thresholds, limits, pacing, retries and timeouts |
| `app.destination.content.daily-token-budget` | `DESTINATION_AI_DAILY_TOKEN_BUDGET` | 2,000,000 | tokens a UTC day before refreshes and staff requests wait |
| `app.destination.content.daily-token-hard-limit` | `DESTINATION_AI_DAILY_TOKEN_HARD_LIMIT` | 5,000,000 | tokens a UTC day before readers' generations wait too |
| `app.destination.content.max-generations-per-user-per-day` | `DESTINATION_AI_MAX_GENERATIONS_PER_USER` | 30 | generations one reader may start per UTC day |
| `app.destination.content.*` | | see `application.yml` | freshness per section, retry and timeout delays, refresh batch |
| `app.destination.trips.itinerary-quota-entitlement` | `DESTINATION_ITINERARY_QUOTA_ENTITLEMENT` | `AI_ITINERARIES_PER_MONTH` | the entitlement holding the monthly itinerary quota |
| `app.destination.trips.plan-url` | `DESTINATION_PLAN_URL` | `http://localhost:3000/trips/{planId}` | the plan's page, linked from push and email |
| `app.destination.visas.dataset-url` | `DESTINATION_VISA_DATASET_URL` | the passport-index tidy ISO2 CSV | where the visa rules are downloaded from |
| `app.destination.visas.*` | | see `application.yml` | full-import threshold, guidance freshness, retry and timeouts |
| `app.destination.social.*` | | see `application.yml` | reports that hide a review, question length and timeout |
| `app.destination.search.embedding-model` | `DESTINATION_EMBEDDING_MODEL` | empty | the embedding model; empty for the worker's own |
| `app.destination.search.*` | | see `application.yml` | dimensions, batch size, timeouts, the most results |
| `app.destination.trips.*` | | see `application.yml` | trip length, places per plan, candidates per destination, timeout |

## Testing

`mvn test` runs the unit tests, with no Docker: the Wikidata and Commons clients and the visa dataset download against mocked HTTP (parsing, pacing, retries, the licence filter), the visa dataset parser, entitlement and quota parsing, the destination policy, import naming and the architecture rules. `mvn verify` adds the integration tests, against the project's PostGIS and pgvector image (built once from `docker/postgres` as `triphuddle/postgres-test`; failsafe passes it to test-support), with Flyway building the schema and Hibernate validating it. Wikimedia and the visa dataset are mocks, the clock can be moved, RabbitMQ is replaced by a recorder, and the AI worker is answered by hand through `AiReplyListener`:

- `PlacesIT`: access with the user's or the group's license and its 48 hours of grace (DST-1); the tree, unique slugs and IDs, moves, merges, hiding and deleting (DST-2); search by any name, accents and typos, distance, map areas, subtrees and ranking (DST-4).
- `ImportsAndCategoriesIT`: imports of regions, cities and notable places with photos, again over existing places, and their failures (DST-3); photos and the category tree.
- `ContentIT`: content written once per place, section and language and shared, the English stand-in, failures, refreshes, late answers, the daily budget, the hard limit and each reader's share, reports and regeneration (DST-5).
- `TripsIT`: plans' limits, owners and groups; itineraries linking only known places, the quota taken and given back, overdue jobs, day rewrites and edits (DST-6).
- `VisasIT`: the rule import, each traveller's easiest passport, permits lasting the trip, who sees whom, shared guidance, retries and limits (DST-7).
- `SocialAndSemanticIT`: favourites and visits, one review per user and place, three reports hiding a review, questions (DST-8); place and query embeddings, the cache, similar places and retries (DST-9); and forgetting a deleted user (DST-10).

Each integration test empties the `destination_*` tables afterwards. Coverage is enforced: 85% of lines and 75% of branches, unit and integration tests together, excluding DTOs and MapStruct's generated code.

## Schema

Flyway's [V1](../src/main/resources/db/migration/V1__create_destination_schema.sql) creates the extensions, the tables, and what JPA doesn't map: the `geo` column, `destination_norm()` and the search indexes, the visa rules, AI usage and embedding tables, and the partial unique indexes. A database whose tables existed before the migrations is baselined at `V1`, so it skips it. It needs PostGIS and pgvector: docker-compose now builds `docker/postgres`, which adds pgvector to `postgis/postgis:16-3.4` (from the PostgreSQL apt archive, as that image is on Debian bullseye). Run `docker compose build postgres` and recreate the container; the data volume carries over; on an existing volume, also run `CREATE DATABASE jobbie_giggle_destination;`.

### Constraints added later

A category's slug is unique among categories not deleted, and a place has one row per content section and language. Run these on databases created before they were added; skip the second if your schema already has a unique constraint on those columns. V1 creates them; `ddl-auto: validate` doesn't check unique constraints, so run these by hand on a database baselined at `V1`. Creating one fails if duplicates already exist, so run the check query first and settle any rows it returns.

```sql
-- Check: each should return no rows.
SELECT slug FROM destination_categories WHERE deleted_at IS NULL GROUP BY slug HAVING count(*) > 1;
SELECT place_id, section, language_code FROM destination_place_contents
GROUP BY place_id, section, language_code HAVING count(*) > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_category_slug
    ON destination_categories (slug) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_place_content_place_section_language_code
    ON destination_place_contents (place_id, section, language_code);
```
