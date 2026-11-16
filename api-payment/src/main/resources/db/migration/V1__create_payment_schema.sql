-- Tables of api-payment, so ddl-auto: validate passes on a new database. Partial unique indexes and CHECKs, which JPA
-- can't declare, are here too. A database whose tables predate the migrations is baselined at version 1, so this
-- script only runs on an empty one.

CREATE TABLE payment_stripe_events (
    id                  UUID         PRIMARY KEY,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    disabled_at         TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    stripe_event_id     VARCHAR(255) NOT NULL UNIQUE,
    event_type          VARCHAR(255) NOT NULL,
    api_version         VARCHAR(64),
    is_livemode         BOOLEAN      NOT NULL,
    stripe_created_at   TIMESTAMPTZ  NOT NULL,
    payload             TEXT         NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    attempts            INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ,
    processed_at        TIMESTAMPTZ,
    last_error          TEXT
);

-- The job's lookup of due events.
CREATE INDEX payment_stripe_events_due_idx ON payment_stripe_events (stripe_created_at)
    WHERE status = 'PENDING';
CREATE INDEX payment_stripe_events_type_idx ON payment_stripe_events (event_type, stripe_created_at);

CREATE TABLE payment_license_products (
    id                  UUID         PRIMARY KEY,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    disabled_at         TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    license_id          UUID         NOT NULL UNIQUE,
    code                VARCHAR(64)  NOT NULL,
    name                VARCHAR(120) NOT NULL,
    description         TEXT,
    target              VARCHAR(16)  NOT NULL,
    license_status      VARCHAR(16)  NOT NULL,
    price_minor         BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    billing_interval    VARCHAR(16)  NOT NULL,
    catalog_changed_at  TIMESTAMPTZ  NOT NULL,
    stripe_product_id   VARCHAR(255) NOT NULL UNIQUE,
    next_sync_at        TIMESTAMPTZ,
    sync_attempts       INTEGER      NOT NULL DEFAULT 0,
    last_sync_error     TEXT,
    synced_at           TIMESTAMPTZ
);

CREATE INDEX payment_license_products_sync_idx ON payment_license_products (next_sync_at)
    WHERE next_sync_at IS NOT NULL;

CREATE TABLE payment_license_prices (
    id                  UUID         PRIMARY KEY,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    disabled_at         TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    license_id          UUID         NOT NULL,
    amount_minor        BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    billing_interval    VARCHAR(16)  NOT NULL,
    lookup_key          VARCHAR(255) NOT NULL UNIQUE,
    stripe_price_id     VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE payment_subscriptions (
    id                          UUID         PRIMARY KEY,
    created_at                  TIMESTAMPTZ  NOT NULL,
    updated_at                  TIMESTAMPTZ  NOT NULL,
    disabled_at                 TIMESTAMPTZ,
    deleted_at                  TIMESTAMPTZ,
    license_subscription_id     UUID         NOT NULL UNIQUE,
    status                      VARCHAR(16)  NOT NULL,
    -- The details are null while only an activation or end has arrived.
    license_id                  UUID,
    subscriber_type             VARCHAR(16),
    subscriber_id               UUID,
    purchased_by_user_id        UUID,
    billing_interval            VARCHAR(16),
    list_price_minor            BIGINT,
    first_price_minor           BIGINT,
    currency                    VARCHAR(3),
    promotion_periods           INTEGER,
    pending_created_at          TIMESTAMPTZ,
    pending_expires_at          TIMESTAMPTZ,
    activated_at                TIMESTAMPTZ,
    ended_at                    TIMESTAMPTZ,
    license_end_status          VARCHAR(16),
    end_reason                  VARCHAR(32),
    stripe_customer_id          VARCHAR(255),
    stripe_checkout_session_id  VARCHAR(255),
    stripe_checkout_url         TEXT,
    stripe_checkout_expires_at  TIMESTAMPTZ,
    stripe_subscription_id      VARCHAR(255) UNIQUE,
    paid_at                     TIMESTAMPTZ,
    refund_due_at               TIMESTAMPTZ,
    is_cancel_at_period_end     BOOLEAN      NOT NULL DEFAULT FALSE,
    reconciled_at               TIMESTAMPTZ
);

CREATE INDEX payment_subscriptions_subscriber_idx ON payment_subscriptions (subscriber_type, subscriber_id);

CREATE INDEX payment_subscriptions_reconcile_idx ON payment_subscriptions (reconciled_at NULLS FIRST)
    WHERE status IN ('ACTIVE', 'PENDING');

CREATE TABLE payment_invoices (
    id                       UUID         PRIMARY KEY,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    disabled_at              TIMESTAMPTZ,
    deleted_at               TIMESTAMPTZ,
    stripe_invoice_id        VARCHAR(255) NOT NULL UNIQUE,
    number                   VARCHAR(64),
    license_subscription_id  UUID         NOT NULL,
    license_id               UUID,
    subscriber_type          VARCHAR(16)  NOT NULL,
    subscriber_id            UUID         NOT NULL,
    stripe_subscription_id   VARCHAR(255) NOT NULL,
    stripe_payment_intent_id VARCHAR(255),
    billing_reason           VARCHAR(32),
    status                   VARCHAR(16)  NOT NULL,
    currency                 VARCHAR(3)   NOT NULL,
    total_minor              BIGINT       NOT NULL,
    tax_minor                BIGINT       NOT NULL,
    amount_paid_minor        BIGINT       NOT NULL,
    amount_refunded_minor    BIGINT       NOT NULL DEFAULT 0,
    hosted_invoice_url       TEXT,
    invoice_pdf_url          TEXT,
    stripe_created_at        TIMESTAMPTZ  NOT NULL,
    paid_at                  TIMESTAMPTZ
);

CREATE INDEX payment_invoices_subscriber_idx ON payment_invoices (subscriber_type, subscriber_id, stripe_created_at);
CREATE INDEX payment_invoices_payment_intent_idx ON payment_invoices (stripe_payment_intent_id);

CREATE TABLE payment_refunds (
    id                  UUID         PRIMARY KEY,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    disabled_at         TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    invoice_id          UUID         NOT NULL REFERENCES payment_invoices (id),
    stripe_refund_id    VARCHAR(255) NOT NULL UNIQUE,
    amount_minor        BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    reason              VARCHAR(64),
    created_by_user_id  UUID,
    stripe_created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX payment_refunds_invoice_idx ON payment_refunds (invoice_id, stripe_created_at);

CREATE TABLE payment_customers (
    id                  UUID         PRIMARY KEY,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    disabled_at         TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    subscriber_type     VARCHAR(16)  NOT NULL,
    subscriber_id       UUID         NOT NULL,
    stripe_customer_id  VARCHAR(255) NOT NULL UNIQUE,
    UNIQUE (subscriber_type, subscriber_id)
);

CREATE TABLE payment_user_contacts (
    id                  UUID         PRIMARY KEY,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    disabled_at         TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    user_id             UUID         NOT NULL UNIQUE,
    -- Null while only a disable or delete has arrived.
    email               VARCHAR(255),
    first_name          VARCHAR(255),
    last_name           VARCHAR(255),
    is_email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    contact_changed_at  TIMESTAMPTZ
);

CREATE TABLE payment_group_admins (
    id                   UUID        PRIMARY KEY,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    disabled_at          TIMESTAMPTZ,
    deleted_at           TIMESTAMPTZ,
    group_membership_id  UUID        NOT NULL UNIQUE,
    group_id             UUID        NOT NULL,
    user_id              UUID        NOT NULL
);

CREATE INDEX payment_group_admins_group_idx ON payment_group_admins (group_id, user_id);
