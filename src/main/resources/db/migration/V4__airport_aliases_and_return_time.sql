-- farewatch :: Korean search aliases + round-trip return time window (V4)

-- Korean (and other) alias keywords for airports so "서울", "도쿄" etc. match.
-- Populated by AirportSeeder from classpath:data/airports-ko.tsv.
ALTER TABLE airport ADD COLUMN aliases TEXT;

-- Round-trip return-leg departure time window (nullable, like the outbound one).
ALTER TABLE watch ADD COLUMN return_time_from TIME;
ALTER TABLE watch ADD COLUMN return_time_to   TIME;
