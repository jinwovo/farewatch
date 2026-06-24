package com.portfolio.farewatch.web;

import com.portfolio.farewatch.service.PollService;
import com.portfolio.farewatch.service.WatchService;
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

	public WatchController(WatchService watchService, PollService pollService) {
		this.watchService = watchService;
		this.pollService = pollService;
	}

	@PostMapping
	public ResponseEntity<WatchResponse> create(@Valid @RequestBody CreateWatchRequest request) {
		WatchResponse body = WatchResponse.from(watchService.create(request));
		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	@GetMapping
	public List<WatchResponse> list(@RequestParam(required = false) String userRef) {
		return watchService.list(userRef).stream().map(WatchResponse::from).toList();
	}

	@GetMapping("/{id}")
	public WatchResponse get(@PathVariable UUID id) {
		return WatchResponse.from(watchService.get(id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		watchService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/poll")
	public PollResultResponse poll(@PathVariable UUID id) {
		return pollService.poll(id);
	}

	@GetMapping("/{id}/prices")
	public List<PricePointResponse> prices(@PathVariable UUID id) {
		return watchService.priceHistory(id);
	}
}
