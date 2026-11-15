# stripe

A library that holds the Stripe client and webhook verification. It has no endpoints, no port and no data. Only `api-payment` uses it, by importing `StripeConfig`.

## In short

A small library that keeps everything Stripe-specific in one place: the configured client, webhook signature checks, and thin wrappers for customers, checkout, subscriptions, invoices, refunds and the catalogue. api-payment is its only user.

**Stack:** stripe-java 34.1.0, pinned to Stripe API version `2026-10-28.endive`.

## User stories

- As api-payment, I verify that a webhook really comes from Stripe and is recent, before trusting it.
- As api-payment, I create customers and refunds with idempotency keys, so a retry never makes two customers or refunds twice.
- As an operator, I can't start the service with a missing key, a wrong key type, or a Stripe API version the code wasn't written for.

## Requirements

- **STR-1** Webhooks are accepted only with a valid signature within the tolerance.
- **STR-2** Customers and refunds are created with idempotency keys.
- **STR-3** Startup fails without valid keys, or when the configured API version differs from stripe-java's.

---

## Technical reference

The sections below describe how it works.

## What it provides

| Bean | Purpose |
|---|---|
| `StripeClient` | stripe-java's client, built from `integration.stripe.*` |
| `StripeWebhooks` | `verify(payload, signatureHeader)` checks a webhook's signature and age and returns the `Event`; `parse(payload)` reads a stored event back; `isReadable(event)` says whether the event is in the API version the models are made for |

| `StripeCustomerClient` | `create` a customer with an idempotency key; `updateContact` |
| `StripeCheckoutClient` | `retrieve` a session's state; `createSubscriptionSession`: subscription mode, card and Link (cards bring Apple Pay and Google Pay), Stripe Tax, a required billing address saved on the customer, tax ID collection, and either our coupon or no promotion code box; `ensureCoupon` with our own ID, at most one redemption; `expireIfOpen` |
| `StripeSubscriptionClient` | `retrieve` status and current period (read from the item, where the API keeps it now); `cancelNow` without proration or a final invoice; `setCancelAtPeriodEnd`; `switchPrice` to a new price and coupon, starting a new period now and charging the prorated difference at once, all or nothing |
| `StripeInvoiceClient` | `paidPaymentIntentId`: the payment intent that paid an invoice, from its invoice payments |
| `StripeRefundClient` | `create` a refund from a payment intent, with an idempotency key; `of` converts Stripe's refund from an event |
| `StripeCatalogClient` | `upsertProduct` creates a Product with our own ID, or updates it; `ensurePrice` finds a Price by lookup key or creates it, recurring and tax-inclusive; `setDefaultPrice` |

`StripeSignatureException` is thrown for a missing or wrong signature, or one older than the tolerance.

## Settings

| Property | Env | Default | Meaning |
|---|---|---|---|
| `integration.stripe.secret-key` | `STRIPE_SECRET_KEY` | none, required | `sk_...` or `rk_...`; test-mode keys locally |
| `integration.stripe.webhook-secret` | `STRIPE_WEBHOOK_SECRET` | none, required | the endpoint's `whsec_...` signing secret |
| `integration.stripe.api-version` | | none, required | must equal stripe-java's `Stripe.API_VERSION` |
| `integration.stripe.webhook-tolerance-seconds` | | 300 | the oldest a signature may be |
| `integration.stripe.connect-timeout-ms` / `read-timeout-ms` | | 10000 / 30000 | API call timeouts |
| `integration.stripe.max-network-retries` | | 2 | retries of failed calls; stripe-java adds idempotency keys |

Failed API calls throw `StripeIntegrationException`, with Stripe's HTTP status and error code.

Startup fails when a key or secret is missing or has the wrong prefix, or when the API versions differ.

## API version

stripe-java sends the API version it was generated for on every call. Its models only read objects in that version. That makes `stripe.version` in the root pom the API version pin: 34.1.0 is `2026-10-28.endive`.

The webhook endpoint in Stripe must use the same version, because Stripe renders event objects in the endpoint's version. An upgrade therefore means changing the dependency, `integration.stripe.api-version` and the endpoint's version together.
