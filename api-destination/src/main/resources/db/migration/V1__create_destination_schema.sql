-- Tables of api-destination's JPA entities, as Hibernate maps them, so ddl-auto: validate passes on a new database.
-- A database whose tables predate the migrations is baselined at version 1, so this script only runs on an empty one.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;


create table destination_categories (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    depth integer not null,
    description TEXT,
    name varchar(120) not null,
    path TEXT not null,
    slug varchar(64) not null,
    sort_order integer not null,
    parent_id uuid,
    primary key (id)
);

create table destination_category_wikidata_classes (
    category_id uuid not null,
    class_id varchar(32) not null,
    primary key (category_id, class_id)
);

create table destination_content_reports (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    reason TEXT not null,
    reported_by_user_id uuid not null,
    resolution varchar(16),
    resolved_at timestamp(6) with time zone,
    resolved_by_user_id uuid,
    content_id uuid not null,
    primary key (id)
);

create table destination_countries (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    alpha2 varchar(2) not null,
    country_id uuid not null,
    primary key (id),
    constraint uk_country_copy_country unique (country_id)
);

create table destination_group_members (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    group_id uuid not null,
    group_membership_id uuid not null,
    role varchar(16) not null,
    user_id uuid not null,
    primary key (id),
    constraint uk_group_member_group_membership unique (group_membership_id)
);

create table destination_import_jobs (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    country_code varchar(2) not null,
    country_wikidata_id varchar(32),
    error TEXT,
    finished_at timestamp(6) with time zone,
    heartbeat_at timestamp(6) with time zone,
    is_including_photos boolean not null,
    is_including_pois boolean not null,
    min_city_population bigint not null,
    min_poi_sitelinks integer not null,
    photos_added integer not null,
    places_created integer not null,
    places_updated integer not null,
    requested_by_user_id uuid not null,
    stage varchar(16),
    started_at timestamp(6) with time zone,
    status varchar(16) not null check (status in ('QUEUED','RUNNING','COMPLETED','FAILED')),
    primary key (id)
);

create table destination_itineraries (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    error TEXT,
    generated_at timestamp(6) with time zone,
    language_code varchar(8) not null,
    model varchar(64),
    pending_job_id uuid,
    requested_at timestamp(6) with time zone,
    requested_by_user_id uuid not null,
    status varchar(16) not null check (status in ('GENERATING','READY','FAILED')),
    summary TEXT,
    plan_id uuid not null,
    primary key (id),
    constraint uk_itinerary_pending_job unique (pending_job_id)
);

create table destination_itinerary_days (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    date date not null,
    day_number integer not null,
    pending_job_id uuid,
    requested_at timestamp(6) with time zone,
    summary TEXT,
    title varchar(200) not null,
    itinerary_id uuid not null,
    primary key (id),
    constraint uk_itinerary_day_itinerary_day_number unique (itinerary_id, day_number),
    constraint uk_itinerary_day_pending_job unique (pending_job_id)
);

create table destination_itinerary_items (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    description TEXT,
    duration_minutes integer,
    place_id uuid,
    position integer not null,
    time_of_day varchar(16) not null check (time_of_day in ('MORNING','AFTERNOON','EVENING')),
    tips TEXT,
    title varchar(200) not null,
    day_id uuid not null,
    primary key (id)
);

create table destination_license_subscriptions (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    current_period_end timestamp(6) with time zone not null,
    ended_at timestamp(6) with time zone,
    entitlements JSONB not null,
    license_code varchar(64),
    license_id uuid not null,
    subscriber_id uuid not null,
    subscriber_type varchar(16) not null check (subscriber_type in ('USER','GROUP')),
    subscription_id uuid not null,
    primary key (id),
    constraint uk_license_access_subscription unique (subscription_id)
);

create table destination_place_categories (
    place_id uuid not null,
    category_id uuid not null,
    primary key (place_id, category_id)
);

create table destination_place_contents (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    citations JSONB not null,
    content JSONB,
    expires_at timestamp(6) with time zone,
    failed_attempts integer not null,
    generated_at timestamp(6) with time zone,
    language_code varchar(8) not null,
    last_error TEXT,
    last_viewed_at timestamp(6) with time zone,
    model varchar(64),
    pending_job_id uuid,
    pending_priority varchar(16) check (pending_priority in ('VIEW','REFRESH','STAFF')),
    requested_at timestamp(6) with time zone,
    section varchar(16) not null check (section in ('OVERVIEW','OPINIONS','PRACTICAL')),
    status varchar(16) not null check (status in ('PENDING','READY','FAILED')),
    place_id uuid not null,
    primary key (id),
    constraint uk_place_content_place_section_language_code unique (place_id, section, language_code),
    constraint uk_place_content_pending_job unique (pending_job_id)
);

create table destination_place_marks (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    kind varchar(16) not null check (kind in ('FAVOURITE','VISITED')),
    place_id uuid not null,
    user_id uuid not null,
    visited_on date,
    primary key (id),
    constraint uk_place_mark_user_place_kind unique (user_id, place_id, kind)
);

create table destination_place_names (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    is_alias boolean not null,
    language_code varchar(8) not null,
    name varchar(200) not null,
    place_id uuid not null,
    primary key (id)
);

create table destination_place_photos (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    author TEXT,
    height integer not null,
    is_attribution_required boolean not null,
    is_primary boolean not null,
    license_name varchar(64) not null,
    license_url TEXT,
    mime_type varchar(64),
    source varchar(32) not null check (source in ('WIKIMEDIA_COMMONS')),
    source_file_name varchar(255) not null,
    source_page_url TEXT,
    source_url TEXT not null,
    storage_path varchar(255),
    width integer not null,
    place_id uuid not null,
    primary key (id)
);

create table destination_place_questions (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    answer JSONB,
    answered_at timestamp(6) with time zone,
    citations JSONB not null,
    error TEXT,
    language_code varchar(8) not null,
    model varchar(64),
    pending_job_id uuid,
    place_id uuid not null,
    question TEXT not null,
    requested_at timestamp(6) with time zone not null,
    status varchar(16) not null check (status in ('PENDING','READY','FAILED')),
    user_id uuid not null,
    primary key (id),
    constraint uk_place_question_pending_job unique (pending_job_id)
);

create table destination_place_reviews (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    body TEXT,
    hidden_at timestamp(6) with time zone,
    hidden_by_user_id uuid,
    hidden_reason varchar(500),
    is_hidden boolean not null,
    language_code varchar(8) not null,
    open_report_count integer not null,
    place_id uuid not null,
    rating integer not null,
    title varchar(120),
    user_id uuid not null,
    visited_on date,
    primary key (id)
);

create table destination_place_tags (
    place_id uuid not null,
    tag varchar(64) not null,
    primary key (place_id, tag)
);

create table destination_places (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    country_code varchar(2) not null,
    depth integer not null,
    is_featured boolean not null,
    kind varchar(16) not null check (kind in ('REGION','CITY','DISTRICT','AREA','POI')),
    latitude float(53),
    longitude float(53),
    merged_into_id uuid,
    name varchar(200) not null,
    osm_id varchar(40),
    path TEXT not null,
    population bigint,
    slug varchar(220) not null,
    status varchar(16) not null check (status in ('ACTIVE','HIDDEN','MERGED')),
    wikidata_id varchar(32),
    parent_id uuid,
    primary key (id),
    constraint uk_place_slug unique (slug),
    constraint uk_place_wikidata unique (wikidata_id),
    constraint uk_place_osm unique (osm_id)
);

create table destination_review_reports (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    reason varchar(500) not null,
    resolved_at timestamp(6) with time zone,
    resolved_by_user_id uuid,
    status varchar(16) not null check (status in ('OPEN','KEPT','HIDDEN')),
    user_id uuid not null,
    review_id uuid not null,
    primary key (id),
    constraint uk_review_report_review_user unique (review_id, user_id)
);

create table destination_traveller_documents (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    changed_at timestamp(6) with time zone not null,
    nationalities JSONB not null,
    residence_permits JSONB not null,
    user_id uuid not null,
    primary key (id),
    constraint uk_traveller_documents_user unique (user_id)
);

create table destination_trip_plan_interests (
    plan_id uuid not null,
    interest varchar(32) not null,
    primary key (plan_id, interest)
);

create table destination_trip_plan_must_sees (
    plan_id uuid not null,
    place_id uuid not null,
    primary key (plan_id, place_id)
);

create table destination_trip_plan_places (
    plan_id uuid not null,
    place_id uuid not null,
    position integer not null,
    primary key (plan_id, position)
);

create table destination_trip_plans (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    adults integer not null,
    budget_level varchar(16) not null check (budget_level in ('BUDGET','MODERATE','LUXURY')),
    children integer not null,
    created_by_user_id uuid not null,
    end_date date not null,
    language_code varchar(8) not null,
    notes TEXT,
    owner_id uuid not null,
    owner_type varchar(16) not null check (owner_type in ('USER','GROUP')),
    pace varchar(16) not null check (pace in ('RELAXED','BALANCED','PACKED')),
    start_date date not null,
    title varchar(120) not null,
    primary key (id)
);

create table destination_visa_guidance (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    allowed_days integer,
    citations JSONB not null,
    content JSONB,
    destination_code varchar(2) not null,
    expires_at timestamp(6) with time zone,
    generated_at timestamp(6) with time zone,
    guidance_key varchar(120) not null,
    language_code varchar(8) not null,
    last_error TEXT,
    model varchar(64),
    passport_code varchar(2) not null,
    pending_job_id uuid,
    permit_codes varchar(64) not null,
    requested_at timestamp(6) with time zone,
    requirement varchar(24) check (requirement in ('CITIZEN','VISA_FREE','VISA_ON_ARRIVAL','ETA','E_VISA','VISA_REQUIRED','NO_ADMISSION')),
    status varchar(16) not null check (status in ('PENDING','READY','FAILED')),
    primary key (id),
    constraint uk_visa_guidance_guidance_key unique (guidance_key),
    constraint uk_visa_guidance_pending_job unique (pending_job_id)
);

create table destination_visa_rule_imports (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    error TEXT,
    finished_at timestamp(6) with time zone,
    removed_count integer not null,
    requested_by_user_id uuid,
    rule_count integer not null,
    skipped_count integer not null,
    source_url TEXT not null,
    started_at timestamp(6) with time zone,
    status varchar(16) not null check (status in ('QUEUED','RUNNING','COMPLETED','FAILED')),
    primary key (id)
);

alter table if exists destination_categories
   add constraint FK9jrna9x7r1g8h0ddnfx04lvgx
   foreign key (parent_id)
   references destination_categories;

alter table if exists destination_category_wikidata_classes
   add constraint FK7cyta4as3paqji1th57lcdgak
   foreign key (category_id)
   references destination_categories;

alter table if exists destination_content_reports
   add constraint FKfuucm0ygbd1sf1gekvwnew0ye
   foreign key (content_id)
   references destination_place_contents;

alter table if exists destination_itineraries
   add constraint FKdgfxk2sdamf26n2evu4fnadl9
   foreign key (plan_id)
   references destination_trip_plans;

alter table if exists destination_itinerary_days
   add constraint FKhs3wo54n3l1bkblvlkjvo1016
   foreign key (itinerary_id)
   references destination_itineraries;

alter table if exists destination_itinerary_items
   add constraint FKbkb8txy4cwknsw8p7j22p4dut
   foreign key (day_id)
   references destination_itinerary_days;

alter table if exists destination_place_categories
   add constraint FKrxcaugo55766drqahhlkf5oep
   foreign key (category_id)
   references destination_categories;

alter table if exists destination_place_categories
   add constraint FKbkb9du0vdpyed43sxiapc7naj
   foreign key (place_id)
   references destination_places;

alter table if exists destination_place_contents
   add constraint FK2v6du3ha8gytwpo0f6rm7vbgl
   foreign key (place_id)
   references destination_places;

alter table if exists destination_place_names
   add constraint FK233prbcrcgf9plyv99frm84dg
   foreign key (place_id)
   references destination_places;

alter table if exists destination_place_photos
   add constraint FK5hdn8ggwlp5hm2ph8pb9j84g4
   foreign key (place_id)
   references destination_places;

alter table if exists destination_place_tags
   add constraint FKhb89mvi8b811ncmsco4lbsifo
   foreign key (place_id)
   references destination_places;

alter table if exists destination_places
   add constraint FKc6mcnro8v6907mjntr68slws6
   foreign key (parent_id)
   references destination_places;

alter table if exists destination_review_reports
   add constraint FKdq4pnxnaf3i1klju5s9vul9xk
   foreign key (review_id)
   references destination_place_reviews;

alter table if exists destination_trip_plan_interests
   add constraint FKne0ji2ds4mrdweu0wnyjnx5yb
   foreign key (plan_id)
   references destination_trip_plans;

alter table if exists destination_trip_plan_must_sees
   add constraint FKa09jo0ddbgka99cs0oybd8e5u
   foreign key (plan_id)
   references destination_trip_plans;

alter table if exists destination_trip_plan_places
   add constraint FKi313g8j3qie5imyjyv2hk2ee9
   foreign key (plan_id)
   references destination_trip_plans;

-- A category's slug is unique among categories not deleted; JPA can't declare a partial index.
create unique index uk_category_slug
    on destination_categories (slug) where deleted_at is null;

-- One review per user and place among those not deleted; JPA can't declare a partial index.
create unique index uk_place_review_user_place
    on destination_place_reviews (user_id, place_id) where deleted_at is null;

-- At most one visa rule import queued or running at a time.
create unique index uk_visa_rule_import_active
    on destination_visa_rule_imports ((true)) where status in ('QUEUED', 'RUNNING');

-- Not mapped by JPA: written and read only through SQL in the repositories, so ddl-auto: validate never sees them.

-- Search normalises names the same way in the index and in the query; unaccent is only STABLE, so it is wrapped,
-- with its dictionary named, to be usable in an index.
CREATE FUNCTION destination_norm(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT lower(public.unaccent('public.unaccent'::regdictionary, $1)) $$;

CREATE INDEX ix_place_name_norm_trgm ON destination_place_names USING gin (destination_norm(name) gin_trgm_ops);
CREATE INDEX ix_place_name_norm_fts ON destination_place_names USING gin (to_tsvector('simple', destination_norm(name)));

-- The place's point as geography, kept in step with latitude and longitude, so distances are in metres.
ALTER TABLE destination_places
    ADD COLUMN geo geography(Point, 4326) GENERATED ALWAYS AS (
        CASE WHEN latitude IS NOT NULL AND longitude IS NOT NULL
             THEN ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography END
    ) STORED;
CREATE INDEX ix_place_geo ON destination_places USING gist (geo);

-- What each passport needs for each destination, loaded from the passport-index dataset.
CREATE TABLE destination_visa_rules (
    passport_code    VARCHAR(2)  NOT NULL,
    destination_code VARCHAR(2)  NOT NULL,
    requirement      VARCHAR(32) NOT NULL CHECK (requirement IN ('CITIZEN', 'VISA_FREE', 'VISA_ON_ARRIVAL', 'ETA',
                                                                  'E_VISA', 'VISA_REQUIRED', 'NO_ADMISSION')),
    allowed_days     INTEGER,
    import_id        UUID        NOT NULL,
    updated_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_visa_rules PRIMARY KEY (passport_code, destination_code)
);
CREATE INDEX ix_visa_rule_destination ON destination_visa_rules (destination_code);
CREATE INDEX ix_visa_rule_import ON destination_visa_rules (import_id);

-- AI tokens spent each day, against the daily bulk budget.
CREATE TABLE destination_ai_usage (
    day           DATE    NOT NULL,
    input_tokens  BIGINT  NOT NULL DEFAULT 0,
    output_tokens BIGINT  NOT NULL DEFAULT 0,
    jobs          INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_usage PRIMARY KEY (day)
);

-- Generations each user asked for each day.
CREATE TABLE destination_ai_user_usage (
    user_id     UUID    NOT NULL,
    day         DATE    NOT NULL,
    generations INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_user_usage PRIMARY KEY (user_id, day)
);

-- Units of a license entitlement used by a user or group in a period.
CREATE TABLE destination_ai_quota_usage (
    owner_type      VARCHAR(16)  NOT NULL CHECK (owner_type IN ('USER', 'GROUP')),
    owner_id        UUID         NOT NULL,
    entitlement_key VARCHAR(255) NOT NULL,
    period          VARCHAR(32)  NOT NULL,
    used            INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_quota_usage PRIMARY KEY (owner_type, owner_id, entitlement_key, period)
);

-- Each place's embedding (its length must match app.destination.search.embedding-dimensions) and the job
-- computing a new one.
CREATE TABLE destination_place_embeddings (
    place_id         UUID NOT NULL,
    embedding        vector(1536),
    source_hash      TEXT,
    model            TEXT,
    embedded_at      TIMESTAMP(6) WITH TIME ZONE,
    checked_at       TIMESTAMP(6) WITH TIME ZONE,
    pending_job_id   UUID,
    pending_position INTEGER,
    pending_hash     TEXT,
    requested_at     TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_place_embeddings PRIMARY KEY (place_id),
    CONSTRAINT fk_place_embedding_place FOREIGN KEY (place_id) REFERENCES destination_places (id) ON DELETE CASCADE
);
CREATE INDEX ix_place_embedding_pending ON destination_place_embeddings (pending_job_id)
    WHERE pending_job_id IS NOT NULL;
CREATE INDEX ix_place_embedding_vector ON destination_place_embeddings USING hnsw (embedding vector_cosine_ops);

-- The cache of search queries' embeddings, by the normalised query's hash.
CREATE TABLE destination_query_embeddings (
    query_hash     TEXT NOT NULL,
    query_text     TEXT NOT NULL,
    embedding      vector(1536),
    model          TEXT,
    embedded_at    TIMESTAMP(6) WITH TIME ZONE,
    pending_job_id UUID,
    requested_at   TIMESTAMP(6) WITH TIME ZONE,
    failed_at      TIMESTAMP(6) WITH TIME ZONE,
    last_used_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_query_embeddings PRIMARY KEY (query_hash)
);
CREATE INDEX ix_query_embedding_pending ON destination_query_embeddings (pending_job_id)
    WHERE pending_job_id IS NOT NULL;
