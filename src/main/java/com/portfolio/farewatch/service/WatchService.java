package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import com.portfolio.farewatch.web.dto.CreateWatchRequest;
import com.portfolio.farewatch.web.dto.PricePointResponse;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchService {

	private final WatchRepository watches;
	private final PricePointRepository pricePoints;

	public WatchService(WatchRepository watches, PricePointRepository pricePoints) {
		this.watches = watches;
		this.pricePoints = pricePoints;
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
		return pricePoints.findByWatch_IdOrderByObservedAtAsc(watchId).stream()
				.map(PricePointResponse::from)
				.toList();
	}

	@Transactional
	public void delete(UUID id) {
		if (!watches.existsById(id)) {
			throw new NoSuchElementException("watch not found: " + id);
		}
		watches.deleteById(id);
	}
}
