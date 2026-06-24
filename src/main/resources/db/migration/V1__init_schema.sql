-- farewatch :: initial schema (V1)
-- Multi-source fare-watch engine. Surrogate UUID PKs only (no @IdClass —
-- Hibernate 7 / Boot 4.1 SessionFactory bootstrap issue). Time-series price
-- history lives in price_point; alerting is idempotent via price_alert.dedup_key.
-- gen_random_uuid() is built into PostgreSQL core since v13 (no extension needed).

-- Price sources behind the FarePriceProvider abstraction. Kept as a table (not a
-- code enum) so each source can be enabled/disabled and health-tracked at runtime.
CREATE TABLE fare_source (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(40)  NOT NULL UNIQUE,           -- AMADEUS, TRAVELPAYOUTS, SCRAPER_RYANAIR, SIMULATOR
    display_name VARCHAR(120) NOT NULL,
    kind         VARCHAR(20)  NOT NULL CHECK (kind IN ('GDS', 'AGGREGATOR', 'SCRAPER', 'SIMULATOR')),
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- A user's price watch: a route + (flexible) date window + an alert rule.
-- The hourly sweep picks up rows whose next_poll_at <= now() and active.
CREATE TABLE watch (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_ref           VARCHAR(80)  NOT NULL,            -- placeholder until auth (out of scope)
    origin             CHAR(3)      NOT NULL,            -- IATA code
    destination        CHAR(3)      NOT NULL,            -- IATA code
    trip_type          VARCHAR(12)  NOT NULL CHECK (trip_type IN ('ONE_WAY', 'ROUND_TRIP')),
    depart_date_from   DATE         NOT NULL,            -- flexible window; from == to => fixed date
    depart_date_to     DATE         NOT NULL,
    return_date_from   DATE,                             -- NULL for one-way
    return_date_to     DATE,
    passengers         INTEGER      NOT NULL DEFAULT 1 CHECK (passengers > 0),
    cabin              VARCHAR(16)  NOT NULL DEFAULT 'ECONOMY'
                         CHECK (cabin IN ('ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST')),
    currency           CHAR(3)      NOT NULL DEFAULT 'KRW',
    alert_rule         VARCHAR(16)  NOT NULL DEFAULT 'NEW_LOW'
                         CHECK (alert_rule IN ('NEW_LOW', 'BELOW_THRESHOLD', 'DROP_PCT')),
    threshold_amount   NUMERIC(12, 2),                   -- for BELOW_THRESHOLD
    drop_pct           NUMERIC(5, 2),                    -- for DROP_PCT
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    poll_interval_min  INTEGER      NOT NULL DEFAULT 60 CHECK (poll_interval_min >= 5),
    last_polled_at     TIMESTAMPTZ,
    next_poll_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_watch_depart_window CHECK (depart_date_to >= depart_date_from),
    -- surrogate id + this UNIQUE dedupes identical watches per user (no @IdClass)
    CONSTRAINT uq_watch_dedupe UNIQUE
        (user_ref, origin, destination, trip_type, depart_date_from, depart_date_to, cabin, passengers)
);
-- Partial index keeps the due-scan cheap: only active watches are indexed.
CREATE INDEX idx_watch_due ON watch (next_poll_at) WHERE active;

-- Time-series of observed prices: one row per (watch, source, poll) cheapest offer.
CREATE TABLE price_point (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    watch_id     UUID          NOT NULL REFERENCES watch (id) ON DELETE CASCADE,
    source_id    UUID          NOT NULL REFERENCES fare_source (id),
    amount       NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency     CHAR(3)       NOT NULL,
    depart_date  DATE          NOT NULL,                 -- chosen cheapest date within the window
    return_date  DATE,
    deep_link    TEXT,                                   -- book-on-source URL (the "최저가 사이트로 이동")
    observed_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_price_point_watch_time ON price_point (watch_id, observed_at DESC);
CREATE INDEX idx_price_point_watch_amount ON price_point (watch_id, amount);

-- A detected trigger (new low / threshold crossed / drop %). dedup_key makes
-- alerting idempotent: the same drop never fires twice across retries or instances.
CREATE TABLE price_alert (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    watch_id                  UUID          NOT NULL REFERENCES watch (id) ON DELETE CASCADE,
    triggering_price_point_id UUID          NOT NULL REFERENCES price_point (id),
    rule                      VARCHAR(16)   NOT NULL,
    previous_low              NUMERIC(12, 2),
    new_low                   NUMERIC(12, 2) NOT NULL,
    dedup_key                 VARCHAR(200)  NOT NULL UNIQUE,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Per-channel delivery of an alert, with retry bookkeeping (push / email).
CREATE TABLE notification (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id    UUID         NOT NULL REFERENCES price_alert (id) ON DELETE CASCADE,
    channel     VARCHAR(12)  NOT NULL CHECK (channel IN ('PUSH', 'EMAIL')),
    status      VARCHAR(12)  NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'RETRY')),
    attempts    INTEGER      NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ
);
CREATE INDEX idx_notification_status ON notification (status);

-- Seed the source registry. The simulator is always available; the others toggle
-- on once their adapter + credentials are wired in later phases (see ADR-0002).
INSERT INTO fare_source (code, display_name, kind, enabled) VALUES
    ('SIMULATOR',       'Simulator (synthetic price walk)', 'SIMULATOR',  TRUE),
    ('AMADEUS',         'Amadeus Self-Service',             'GDS',        FALSE),
    ('TRAVELPAYOUTS',   'Travelpayouts (Aviasales)',        'AGGREGATOR', FALSE),
    ('SCRAPER_RYANAIR', 'Ryanair (direct, scraper)',        'SCRAPER',    FALSE);
