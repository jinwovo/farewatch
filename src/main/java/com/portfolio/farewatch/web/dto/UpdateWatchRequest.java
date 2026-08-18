package com.portfolio.farewatch.web.dto;

import com.portfolio.farewatch.domain.AlertRule;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Partial watch update; only non-null fields are applied. Covers pause/resume
 * ({@code active}) and alert-condition editing ({@code alertRule} + its parameter).
 */
public record UpdateWatchRequest(
		Boolean active,
		AlertRule alertRule,
		@Positive BigDecimal thresholdAmount,
		@Positive @DecimalMax("90") BigDecimal dropPct) {
}