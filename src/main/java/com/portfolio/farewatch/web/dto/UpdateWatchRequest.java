package com.portfolio.farewatch.web.dto;

/** Partial watch update; only non-null fields are applied. Today that's {@code active} (pause/resume). */
public record UpdateWatchRequest(Boolean active) {
}
