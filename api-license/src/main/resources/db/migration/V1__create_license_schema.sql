-- Tables of api-license, so ddl-auto: validate passes on a new database. Partial unique indexes and CHECKs, which JPA
-- can't declare, are here too. A database whose tables predate the migrations is baselined at version 1, so this
-- script only runs on an empty one.

CREATE TABLE licenses (
    id               UUID PRIMARY KEY,
    code             VARCHAR(64)  NOT NULL,
    name             VARCHAR(120) NOT NULL,
    description      TEXT,
    target           VARCHAR(16)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    price_minor      BIGINT       NOT NULL CHECK (price_minor >= 0),
    billing_interval VARCHAR(16)  NOT NULL,
    published_at     TIMESTAMP(6) WITH TIME ZONE,
    archived_at      TIMESTAMP(6) WITH TIME ZONE,
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    disabled_at      TIMESTAMP(6) WITH TIME ZONE,
    deleted_at       TIMESTAMP(6) WITH TIME ZONE
);
-- A deleted draft frees its code.
CREATE UNIQUE INDEX licenses_code_uq ON licenses (code) WHERE deleted_at IS NULL;

CREATE TABLE license_entitlements (
    id                UUID PRIMARY KEY,
    license_id        UUID        NOT NULL REFERENCES licenses (id),
    entitlement_key   VARCHAR(64) NOT NULL,
    entitlement_value VARCHAR(255),
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    disabled_at       TIMESTAMP(6) WITH TIME ZONE,
    deleted_at        TIMESTAMP(6) WITH TIME ZONE,
    UNIQUE (license_id, entitlement_key)
);

CREATE TABLE license_promotions (
    id                    UUID PRIMARY KEY,
    license_id            UUID         NOT NULL REFERENCES licenses (id),
    name                  VARCHAR(120) NOT NULL,
    code                  VARCHAR(32),
    discount_type         VARCHAR(16)  NOT NULL,
    discount_value        BIGINT       NOT NULL CHECK (discount_value > 0),
    starts_at             TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    ends_at               TIMESTAMP(6) WITH TIME ZONE,
    duration_periods      INTEGER CHECK (duration_periods > 0),
    max_redemptions       INTEGER CHECK (max_redemptions > 0),
    redemption_count      INTEGER      NOT NULL DEFAULT 0,
    is_once_per_customer  BOOLEAN      NOT NULL,
    is_new_customers_only BOOLEAN      NOT NULL,
    min_price_minor       BIGINT       NOT NULL DEFAULT 0 CHECK (min_price_minor >= 0),
    created_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    disabled_at           TIMESTAMP(6) WITH TIME ZONE,
    deleted_at            TIMESTAMP(6) WITH TIME ZONE
);
CREATE UNIQUE INDEX license_promotions_code_uq ON license_promotions (license_id, code)
    WHERE code IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX license_promotions_license_idx ON license_promotions (license_id, starts_at);

CREATE TABLE license_subscriptions (
    id                          UUID PRIMARY KEY,
    license_id                  UUID        NOT NULL REFERENCES licenses (id),
    subscriber_type             VARCHAR(16) NOT NULL,
    subscriber_id               UUID        NOT NULL,
    purchased_by_user_id        UUID        NOT NULL,
    status                      VARCHAR(16) NOT NULL,
    billing_interval            VARCHAR(16) NOT NULL,
    list_price_minor            BIGINT      NOT NULL,
    price_minor                 BIGINT      NOT NULL,
    promotion_id                UUID REFERENCES license_promotions (id),
    promotion_periods_remaining INTEGER,
    current_period_start        TIMESTAMP(6) WITH TIME ZONE,
    current_period_end          TIMESTAMP(6) WITH TIME ZONE,
    is_auto_renew               BOOLEAN     NOT NULL,
    renewal_count               INTEGER     NOT NULL DEFAULT 0,
    canceled_at                 TIMESTAMP(6) WITH TIME ZONE,
    activated_at                TIMESTAMP(6) WITH TIME ZONE,
    activated_by_user_id        UUID,
    ended_at                    TIMESTAMP(6) WITH TIME ZONE,
    end_reason                  VARCHAR(32),
    switched_to_subscription_id UUID,
    is_granted                  BOOLEAN     NOT NULL,
    granted_by_user_id          UUID,
    reminded_period_end         TIMESTAMP(6) WITH TIME ZONE,
    is_billed_by_stripe         BOOLEAN     NOT NULL DEFAULT FALSE,
    past_due_at                 TIMESTAMP(6) WITH TIME ZONE,
    created_at                  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    disabled_at                 TIMESTAMP(6) WITH TIME ZONE,
    deleted_at                  TIMESTAMP(6) WITH TIME ZONE
);
-- One active and one pending subscription per subscriber, whatever the race.
CREATE UNIQUE INDEX license_subscriptions_active_uq ON license_subscriptions (subscriber_type, subscriber_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;
CREATE UNIQUE INDEX license_subscriptions_pending_uq ON license_subscriptions (subscriber_type, subscriber_id)
    WHERE status = 'PENDING_PAYMENT' AND deleted_at IS NULL;
CREATE INDEX license_subscriptions_subscriber_idx ON license_subscriptions (subscriber_type, subscriber_id, created_at);
CREATE INDEX license_subscriptions_renewal_idx ON license_subscriptions (current_period_end) WHERE status = 'ACTIVE';
CREATE INDEX license_subscriptions_pending_idx ON license_subscriptions (created_at) WHERE status = 'PENDING_PAYMENT';

CREATE TABLE license_promotion_redemptions (
    id              UUID PRIMARY KEY,
    promotion_id    UUID        NOT NULL REFERENCES license_promotions (id),
    subscriber_type VARCHAR(16) NOT NULL,
    subscriber_id   UUID        NOT NULL,
    subscription_id UUID        NOT NULL UNIQUE REFERENCES license_subscriptions (id),
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    disabled_at     TIMESTAMP(6) WITH TIME ZONE,
    deleted_at      TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX license_promotion_redemptions_subscriber_idx
    ON license_promotion_redemptions (promotion_id, subscriber_type, subscriber_id);

CREATE TABLE license_group_admins (
    id                  UUID PRIMARY KEY,
    group_membership_id UUID NOT NULL UNIQUE,
    group_id            UUID NOT NULL,
    user_id             UUID NOT NULL,
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    disabled_at         TIMESTAMP(6) WITH TIME ZONE,
    deleted_at          TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX license_group_admins_group_user_idx ON license_group_admins (group_id, user_id);
