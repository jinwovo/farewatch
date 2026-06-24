package com.portfolio.farewatch.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Result of polling a watch now: the fresh quotes plus the current lowest. */
public record PollResultResponse(
		UUID watchId,
		Instant polledAt,
		List<PricePointResponse> newPrices,
		BigDecimal lowestAmount,
		String lowestCurrency,
		LocalDate lowestDepartDate,
		String lowestDeepLink,
		boolean newLow) {
}
