-- Tables for Currency, Language and Country, matching the JPA entities (ddl-auto: validate checks them).
-- A database that already has these tables is baselined at version 1, so this script only runs on an empty one.

CREATE TABLE currencies (
    id              UUID         NOT NULL,
    code            VARCHAR(3)   NOT NULL,
    name            VARCHAR(255) NOT NULL,
    symbol          VARCHAR(8)   NOT NULL,
    symbol_position VARCHAR(8)   NOT NULL,
    minor_units     INTEGER      NOT NULL,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    disabled_at     TIMESTAMP(6) WITH TIME ZONE,
    deleted_at      TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_currencies PRIMARY KEY (id),
    CONSTRAINT uk_currency_code UNIQUE (code),
    CONSTRAINT ck_currency_symbol_position CHECK (symbol_position IN ('PREFIX', 'SUFFIX'))
);

CREATE TABLE languages (
    id               UUID         NOT NULL,
    code             VARCHAR(8)   NOT NULL,
    name             VARCHAR(255) NOT NULL,
    native_name      VARCHAR(255) NOT NULL,
    is_right_to_left BOOLEAN      NOT NULL,
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    disabled_at      TIMESTAMP(6) WITH TIME ZONE,
    deleted_at       TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_languages PRIMARY KEY (id),
    CONSTRAINT uk_language_code UNIQUE (code)
);

CREATE TABLE countries (
    id                  UUID         NOT NULL,
    alpha2              VARCHAR(2)   NOT NULL,
    alpha3              VARCHAR(3)   NOT NULL,
    numeric_code        VARCHAR(3)   NOT NULL,
    name                VARCHAR(255) NOT NULL,
    calling_code        VARCHAR(8),
    standard_vat_rate   NUMERIC(5, 2),
    is_eu_vat_member    BOOLEAN      NOT NULL,
    is_eu_member        BOOLEAN      NOT NULL,
    is_eea_member       BOOLEAN      NOT NULL,
    is_schengen_member  BOOLEAN      NOT NULL,
    default_currency_id UUID,
    default_language_id UUID,
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    disabled_at         TIMESTAMP(6) WITH TIME ZONE,
    deleted_at          TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_countries PRIMARY KEY (id),
    CONSTRAINT uk_country_alpha2 UNIQUE (alpha2),
    CONSTRAINT uk_country_alpha3 UNIQUE (alpha3),
    CONSTRAINT uk_country_numeric_code UNIQUE (numeric_code),
    CONSTRAINT fk_country_default_currency FOREIGN KEY (default_currency_id) REFERENCES currencies (id),
    CONSTRAINT fk_country_default_language FOREIGN KEY (default_language_id) REFERENCES languages (id)
);

CREATE INDEX idx_country_default_currency ON countries (default_currency_id);
CREATE INDEX idx_country_default_language ON countries (default_language_id);
