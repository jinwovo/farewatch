package com.portfolio.farewatch.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A single source's cheapest offer for a {@link FareQuery}. */
public record FareQuote(
		String sourceCode,
		BigDecimal amount,
		String currency,
		LocalDate departDate,
		LocalDate returnDate,
		String deepLink) {
}
