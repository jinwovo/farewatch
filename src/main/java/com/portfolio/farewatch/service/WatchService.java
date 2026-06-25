package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.PriceAlert;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.NotificationRepository;
import com.portfolio.farewatch.repo.PriceAlertRepository;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import com.portfolio.farewatch.web.dto.AlertResponse;
import com.portfolio.farewatch.web.dto.CalendarCell;
import com.portfolio.farewatch.web.dto.CreateWatchRequest;
import com.portfolio.farewatch.web.dto.NotificationResponse;
import com.portfolio.farewatch.web.dto.PricePointResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchService {

	/** Chart returns at most this many recent points (the client downsamples further). */
	private static final int CHART_MAX_POINTS = 2000;

	private final WatchRepository watches;
	private final PricePointRepository pricePoints;
	private final PriceAlertRepository priceAlerts;
	private final NotificationRepository notifications;

	public WatchService(WatchRepository watches, PricePointRepository pricePoints,
			PriceAlertRepository priceAlerts, NotificationRepository notifications) {
		this.watches = watches;
		this.pricePoints = pricePoints;
		this.priceAlerts = priceAlerts;
		this.notifications = notifications;
	}

	@Transactional
	public Watch create(CreateWatchRequest r) {
		if (r.departDateTo().isBefore(r.departDateFrom())) {
			throw new IllegalArgumentException("departDateTo must be on/after departDateFrom");
		}
		Watch w = new Watch();
		w.setUserRef(r.userRef());
		w.setOrigin(r.origin().toUpperCase());
		w.setDestination(r.destination().toUpperCase());
		w.setTripType(r.tripType());
		w.setDepartDateFrom(r.departDateFrom());
		w.setDepartDateTo(r.departDateTo());
		w.setReturnDateFrom(r.returnDateFrom());
		w.setReturnDateTo(r.returnDateTo());
		w.setDepartTimeFrom(r.departTimeFrom());
		w.setDepartTimeTo(r.departTimeTo());
		w.setReturnTimeFrom(r.returnTimeFrom());
		w.setReturnTimeTo(r.returnTimeTo());
		if (r.passengers() != null) {
			w.setPassengers(r.passengers());
		}
		if (r.cabin() != null) {
			w.setCabin(r.cabin());
		}
		if (r.currency() != null && !r.currency().isBlank()) {
			w.setCurrency(r.currency().toUpperCase());
		}
		if (r.alertRule() != null) {
			w.setAlertRule(r.alertRule());
		}
		w.setThresholdAmount(r.thresholdAmount());
		w.setDropPct(r.dropPct());
		if (r.pollIntervalMin() != null) {
			w.setPollIntervalMin(r.pollIntervalMin());
		}
		w.setNextPollAt(Instant.now());
		return watches.save(w);
	}

	@Transactional(readOnly = true)
	public List<Watch> list(String userRef) {
		return (userRef == null || userRef.isBlank())
				? watches.findAll()
				: watches.findByUserRefOrderByCreatedAtDesc(userRef);
	}

	@Transactional(readOnly = true)
	public Watch get(UUID id) {
		return watches.findById(id)
				.orElseThrow(() -> new NoSuchElementException("watch not found: " + id));
	}

	@Transactional(readOnly = true)
	public List<PricePointResponse> priceHistory(UUID watchId) {
		get(watchId); // 404 if the watch does not exist
		// Bounded to the most recent N (newest first), then re-ordered oldest-first for the chart.
		return pricePoints.findByWatch_IdOrderByObservedAtDesc(watchId, PageRequest.of(0, CHART_MAX_POINTS))
				.reversed().stream()
				.map(PricePointResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<CalendarCell> priceCalendar(UUID watchId) {
		Watch w = get(watchId); // 404 if the watch does not exist
		return pricePoints.cheapestByDepartDate(watchId).stream()
				.map(d -> new CalendarCell(d.getDepartDate(), d.getLowest(), w.getCurrency()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<AlertResponse> alertHistory(UUID watchId) {
		get(watchId); // 404 if the watch does not exist
		List<AlertResponse> out = new ArrayList<>();
		for (PriceAlert alert : priceAlerts.findByWatch_IdOrderByCreatedAtDesc(watchId)) {
			List<NotificationResponse> deliveries = notifications.findByAlert_IdOrderByChannelAsc(alert.getId())
					.stream().map(NotificationResponse::from).toList();
			out.add(AlertResponse.from(alert, deliveries));
		}
		return out;
	}

	@Transactional
	public void delete(UUID id) {
		if (!watches.existsById(id)) {
			throw new NoSuchElementException("watch not found: " + id);
		}
		watches.deleteById(id);
	}
}
