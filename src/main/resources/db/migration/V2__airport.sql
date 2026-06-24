-- farewatch :: airport reference data (V2)
-- Seeded at startup from classpath:data/airports.tsv (OurAirports, public domain):
-- scheduled-service large/medium airports that have an IATA code. This is stable
-- reference data, so it uses a natural key (iata) rather than a surrogate UUID.

CREATE TABLE airport (
    iata         CHAR(3)          PRIMARY KEY,
    name         VARCHAR(200)     NOT NULL,
    municipality VARCHAR(200),
    country      CHAR(2)          NOT NULL,
    lat          DOUBLE PRECISION NOT NULL,
    lon          DOUBLE PRECISION NOT NULL,
    large        BOOLEAN          NOT NULL DEFAULT FALSE
);

-- Functional indexes for case-insensitive prefix autocomplete on name / city.
CREATE INDEX idx_airport_name_lower ON airport (lower(name));
CREATE INDEX idx_airport_muni_lower ON airport (lower(municipality));
