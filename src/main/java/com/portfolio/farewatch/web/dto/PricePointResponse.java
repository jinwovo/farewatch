package com.portfolio.farewatch.web.dto;

import com.portfolio.farewatch.domain.PricePoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PricePointResponse(
		UUID id,
		String source,
		BigDecimal amount,
		String currency,
		LocalDate departDate,
		LocalDate returnDate,
		String deepLink,
		Instant observedAt) {

	/** Must be called within a transaction — touches the lazy source association. */
	public static PricePointResponse from(PricePoint p) {
		return new PricePointResponse(
				p.getId(), p.getSource().getCode(), p.getAmount(), p.getCurrency(),
				p.getDepartDate(), p.getReturnDate(), p.getDeepLink(), p.getObservedAt());
	}
}
