package com.portfolio.farewatch.domain;

/** Category of a price source — matches fare_source.kind CHECK values. */
public enum SourceKind {
	GDS,
	AGGREGATOR,
	SCRAPER,
	SIMULATOR
}
