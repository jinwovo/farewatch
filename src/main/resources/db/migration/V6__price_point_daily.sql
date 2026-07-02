-- farewatch :: price_point retention (V6)
-- price_point grows without bound (~hourly rows per watch, forever). Retention policy:
-- keep RAW points for `farewatch.retention.raw-days` (90d default), roll older days up
-- into one row per (watch, day) — min/max/avg/count survive, so the all-time low and
-- long-horizon stats stay answerable after the raw rows are purged. Rows referenced by
-- a price_alert are exempt from the purge (FK) and from the rollup (no double counting):
-- alerts keep their full triggering evidence forever.

CREATE TABLE price_point_daily (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    watch_id     UUID           NOT NULL REFERENCES watch (id) ON DELETE CASCADE,
    day          DATE           NOT NULL,                  -- UTC day the raw points were observed
    min_amount   NUMERIC(12, 2) NOT NULL,
    max_amount   NUMERIC(12, 2) NOT NULL,
    avg_amount   NUMERIC(12, 2) NOT NULL,
    sample_count INTEGER        NOT NULL CHECK (sample_count > 0),
    currency     CHAR(3)        NOT NULL,
    CONSTRAINT uq_price_point_daily UNIQUE (watch_id, day)
);
-- all-time-low lookup over rolled-up history
CREATE INDEX idx_ppd_watch_min ON price_point_daily (watch_id, min_amount);

-- the retention scan asks "what is the oldest raw point?" — global, not per watch
CREATE INDEX idx_price_point_observed ON price_point (observed_at);
