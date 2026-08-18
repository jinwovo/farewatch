package com.portfolio.farewatch.web.dto;

import com.portfolio.farewatch.domain.AlertRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of the global alert feed: the alert plus enough watch context (route,
 * Korean names, currency, booking deep link) to render it outside the watch page.
 */
public record AlertFeedItem(
		UUID id,
		UUID watchId,
		String origin,
		String destination,
		String originKorean,
		String destKorean,
		String currency,
		AlertRule rule,
		BigDecimal previousLow,
		BigDecimal newLow,
		boolean mistakeFare,
		String deepLink,
		Instant createdAt,
		List<NotificationResponse> notifications) {
}