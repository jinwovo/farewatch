package com.portfolio.farewatch.web;

import com.portfolio.farewatch.service.AlertFeedService;
import com.portfolio.farewatch.web.dto.AlertFeedItem;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

	private final AlertFeedService alertFeedService;

	public AlertController(AlertFeedService alertFeedService) {
		this.alertFeedService = alertFeedService;
	}

	/** Newest alerts across all watches (global feed), capped server-side. */
	@GetMapping
	public List<AlertFeedItem> recent(@RequestParam(defaultValue = "50") int limit) {
		return alertFeedService.recent(limit);
	}
}