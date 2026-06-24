package com.portfolio.farewatch.web.dto;

import com.portfolio.farewatch.domain.AlertRule;
import com.portfolio.farewatch.domain.PriceAlert;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AlertResponse(
		UUID id,
		AlertRule rule,
		BigDecimal previousLow,
		BigDecimal newLow,
		Instant createdAt,
		List<NotificationResponse> notifications) {

	public static AlertResponse from(PriceAlert a, List<NotificationResponse> notifications) {
		return new AlertResponse(a.getId(), a.getRule(), a.getPreviousLow(), a.getNewLow(), a.getCreatedAt(),
				notifications);
	}
}
