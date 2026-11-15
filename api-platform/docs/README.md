# api-platform

Owns the reference data every other part of the product relies on: countries, currencies and languages, on port **8082**. It also stores feature flags.

## Data

Countries, currencies and languages are in PostgreSQL (`jobbie_giggle_platform`); feature flags are documents in MongoDB.

```mermaid
erDiagram
    COUNTRY }o--o| CURRENCY : "default currency"
    COUNTRY }o--o| LANGUAGE : "default language"
    COUNTRY {
        uuid id PK
        string alpha2
        string alpha3
        string numericCode
        string name
        string callingCode
        decimal standardVatRate
        boolean isEuVatMember
        boolean isEuMember
        boolean isEeaMember
        boolean isSchengenMember
    }
    CURRENCY {
        uuid id PK
        string code
        string name
        string symbol
        enum symbolPosition
        int minorUnits
    }
    LANGUAGE {
        uuid id PK
        string code
        string name
        string nativeName
        boolean isRightToLeft
    }
    FEATURE_FLAG {
        objectid id PK
        string flagKey
        string name
        string description
    }
```

Every record also has `createdAt`, `updatedAt`, `disabledAt` and `deletedAt`. Codes are unique: a currency or language by `code`, a country by each of `alpha2`, `alpha3` and `numericCode`.

## Constraints

The codes the services check before saving are unique in the database too, so two requests at once can't both create a code. There are no migrations, and `ddl-auto: validate` doesn't check unique constraints, so run these by hand. Creating one fails if duplicates already exist, so run the check query first and settle any rows it returns.

```sql
-- Check: each should return no rows.
SELECT alpha2 FROM countries GROUP BY alpha2 HAVING count(*) > 1;
SELECT alpha3 FROM countries GROUP BY alpha3 HAVING count(*) > 1;
SELECT numeric_code FROM countries GROUP BY numeric_code HAVING count(*) > 1;
SELECT code FROM currencies GROUP BY code HAVING count(*) > 1;
SELECT code FROM languages GROUP BY code HAVING count(*) > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_country_alpha2 ON countries (alpha2);
CREATE UNIQUE INDEX IF NOT EXISTS uk_country_alpha3 ON countries (alpha3);
CREATE UNIQUE INDEX IF NOT EXISTS uk_country_numeric_code ON countries (numeric_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_currency_code ON currencies (code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_language_code ON languages (code);
```

## Endpoints

The three resources have the same endpoints, under `/api/v1/platform/countries`, `/currencies` and `/languages`.

| Method and path | Who may call it |
|---|---|
| `GET /{resource}`, `GET /{resource}/{id}` | anyone, signed in or not |
| `POST /{resource}`, `PUT /{resource}` | Admin or Support |
| `DELETE /{resource}/{id}/disable` | Admin or Support |
| `DELETE /{resource}/{id}` | Admin or Support |

**Feature flags have no endpoints and no service.** They are meant to be managed by administrators directly in the database.

## Why reads work without a token

```mermaid
flowchart LR
    R[GET without token] --> M{permitted matcher?}
    M -->|yes| A[anonymous authentication]
    A --> P["@PreAuthorize: policy.canGetById / canGetAll"]
    P -->|returns true| OK[200]
    M -->|no| X[401]
```

The read routes are listed in `additionalPermitAllMatchers()`, and `anonymousAuthenticationEnabled()` returns `true` so that the policy is still asked.

## Messages

Publishes one event when a country, currency or language is created, disabled or deleted (nine in all), for example `CountryCreatedEvent(countryId, alpha2)`. Nothing consumes them yet. This service consumes no messages.
