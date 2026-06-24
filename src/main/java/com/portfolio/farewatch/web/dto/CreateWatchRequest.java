package com.portfolio.farewatch.web.dto;

import com.portfolio.farewatch.domain.AlertRule;
import com.portfolio.farewatch.domain.Cabin;
import com.portfolio.farewatch.domain.TripType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Create-watch request. Optional fields fall back to defaults in the service. */
public record CreateWatchRequest(
		@NotBlank String userRef,
		@NotBlank @Pattern(regexp = "(?i)[a-z]{3}", message = "must be a 3-letter IATA code") String origin,
		@NotBlank @Pattern(regexp = "(?i)[a-z]{3}", message = "must be a 3-letter IATA code") String destination,
		@NotNull TripType tripType,
		@NotNull LocalDate departDateFrom,
		@NotNull LocalDate departDateTo,
		LocalDate returnDateFrom,
		LocalDate returnDateTo,
		@Min(1) Integer passengers,
		Cabin cabin,
		String currency,
		AlertRule alertRule,
		@DecimalMin("0.0") BigDecimal thresholdAmount,
		@DecimalMin("0.0") BigDecimal dropPct,
		@Min(5) Integer pollIntervalMin) {
}
