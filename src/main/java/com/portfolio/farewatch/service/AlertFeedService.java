package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.Airport;
import com.portfolio.farewatch.domain.Notification;
import com.portfolio.farewatch.domain.PriceAlert;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.AirportRepository;
import com.portfolio.farewatch.repo.NotificationRepository;
import com.portfolio.farewatch.repo.PriceAlertRepository;
import com.portfolio.farewatch.web.dto.AlertFeedItem;
import com.portfolio.farewatch.web.dto.NotificationResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The global "최근 알림" feed: newest alerts across every watch, enriched with route
 * context and delivery status. Three bounded queries total (alerts fetch-joined,
 * notifications batched by alert id, airports batched by IATA) — no per-row loads.
 */
@Service
public class AlertFeedService {

	private static final int MAX_LIMIT = 100;

	private final PriceAlertRepository priceAlerts;
	private final NotificationRepository notifications;
	private final AirportRepository airports;

	public AlertFeedService(PriceAlertRepository priceAlerts, NotificationRepository notifications,
			AirportRepository airports) {
		this.priceAlerts = priceAlerts;
		this.notifications = notifications;
		this.airports = airports;
	}

	@Transactional(readOnly = true)
	public List<AlertFeedItem> recent(int limit) {
		int capped = Math.max(1, Math.min(limit, MAX_LIMIT));
		List<PriceAlert> alerts = priceAlerts.recentWithContext(PageRequest.of(0, capped));
		if (alerts.isEmpty()) {
			return List.of();
		}

		List<UUID> ids = alerts.stream().map(PriceAlert::getId).toList();
		Map<UUID, List<NotificationResponse>> deliveries = new HashMap<>();
		for (Notification n : notifications.findByAlert_IdInOrderByChannelAsc(ids)) {
			deliveries.computeIfAbsent(n.getAlert().getId(), k -> new ArrayList<>())
					.add(NotificationResponse.from(n));
		}

		Set<String> codes = alerts.stream()
				.flatMap(a -> Stream.of(a.getWatch().getOrigin(), a.getWatch().getDestination()))
				.collect(Collectors.toSet());
		Map<String, Airport> byIata = new HashMap<>();
		airports.findAllById(codes).forEach(a -> byIata.put(a.getIata(), a));

		return alerts.stream().map(a -> {
			Watch w = a.getWatch();
			Airport o = byIata.get(w.getOrigin());
			Airport d = byIata.get(w.getDestination());
			return new AlertFeedItem(
					a.getId(), w.getId(), w.getOrigin(), w.getDestination(),
					o == null ? null : o.getKorean(), d == null ? null : d.getKorean(),
					w.getCurrency(), a.getRule(), a.getPreviousLow(), a.getNewLow(),
					a.isMistakeFare(), a.getTriggeringPricePoint().getDeepLink(),
					a.getCreatedAt(), deliveries.getOrDefault(a.getId(), List.of()));
		}).toList();
	}
}