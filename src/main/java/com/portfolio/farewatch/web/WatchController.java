package com.portfolio.farewatch.web;

import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.notify.NotificationDispatcher;
import com.portfolio.farewatch.repo.AirportRepository;
import com.portfolio.farewatch.service.PollService;
import com.portfolio.farewatch.service.WatchService;
import com.portfolio.farewatch.weather.WeatherEstimate;
import com.portfolio.farewatch.weather.WeatherService;
import com.portfolio.farewatch.web.dto.AlertResponse;
import com.portfolio.farewatch.web.dto.CalendarCell;
import com.portfolio.farewatch.web.dto.CreateWatchRequest;
import com.portfolio.farewatch.web.dto.PollResultResponse;
import com.portfolio.farewatch.web.dto.PricePointResponse;
import com.portfolio.farewatch.web.dto.WatchResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watches")
public class WatchController {

	private final WatchService watchService;
	private final PollService pollService;
	private final NotificationDispatcher notificationDispatcher;
	private final WeatherService weatherService;
	private final AirportRepository airports;

	public WatchController(WatchService watchService, PollService pollService,
			NotificationDispatcher notificationDispatcher, WeatherService weatherService,
			AirportRepository airports) {
		this.watchService = watchService;
		this.pollService = pollService;
		this.notificationDispatcher = notificationDispatcher;
		this.weatherService = weatherService;
		this.airports = airports;
	}

	/** Map a watch to its response, enriched with origin/destination airport display names. */
	private WatchResponse resp(Watch w) {
		return WatchResponse.from(w,
				airports.findById(w.getOrigin()).orElse(null),
				airports.findById(w.getDestination()).orElse(null));
	}

	@PostMapping
	public ResponseEntity<WatchResponse> create(@Valid @RequestBody CreateWatchRequest request) {
		WatchResponse body = resp(watchService.create(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	@GetMapping
	public List<WatchResponse> list(@RequestParam(required = false) String userRef) {
		return watchService.list(userRef).stream().map(this::resp).toList();
	}

	@GetMapping("/{id}")
	public WatchResponse get(@PathVariable UUID id) {
		return resp(watchService.get(id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		watchService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/poll")
	public PollResultResponse poll(@PathVariable UUID id) {
		PollResultResponse result = pollService.poll(id);
		notificationDispatcher.dispatch(100); // deliver any alert this poll just fired
		return result;
	}

	@GetMapping("/{id}/prices")
	public List<PricePointResponse> prices(@PathVariable UUID id) {
		return watchService.priceHistory(id);
	}

	@GetMapping("/{id}/calendar")
	public List<CalendarCell> calendar(@PathVariable UUID id) {
		return watchService.priceCalendar(id);
	}

	@GetMapping("/{id}/alerts")
	public List<AlertResponse> alerts(@PathVariable UUID id) {
		return watchService.alertHistory(id);
	}

	@GetMapping("/{id}/weather")
	public List<WeatherEstimate> weather(@PathVariable UUID id) {
		return weatherService.forWatch(id);
	}
}
