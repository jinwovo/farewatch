-- farewatch :: departure time-of-day window on a watch (V3)
-- Nullable: a watch with no time window tracks any departure time. The UI offers
-- presets (아침 06–12 / 오후 12–18 / 저녁 18–24) and a precise range; both are just
-- a [from, to) pair of TIME values, so one column pair serves both.

ALTER TABLE watch ADD COLUMN depart_time_from TIME;
ALTER TABLE watch ADD COLUMN depart_time_to   TIME;
