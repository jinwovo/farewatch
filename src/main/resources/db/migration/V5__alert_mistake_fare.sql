-- V5: flag statistically-anomalous lows ("mistake fares") for priority alerting.
-- An alert whose triggering price is >= 2.5σ below the route's own price history is
-- suspicious (airline pricing-engine error / sudden flash drop) and gets surfaced first.
ALTER TABLE price_alert ADD COLUMN mistake_fare BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_price_alert_mistake ON price_alert (mistake_fare) WHERE mistake_fare;
