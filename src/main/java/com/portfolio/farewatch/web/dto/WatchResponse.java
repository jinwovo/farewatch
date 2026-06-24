package com.portfolio.farewatch.web.dto;

import com.portfolio.farewatch.domain.AlertRule;
import com.portfolio.farewatch.domain.Cabin;
import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.domain.Watch;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WatchResponse(
		UUID id,
		String userRef,
		String origin,
		String destination,
		TripType tripType,
		LocalDate departDateFrom,
		LocalDate departDateTo,
		LocalDate returnDateFrom,
		LocalDate returnDateTo,
		int passengers,
		Cabin cabin,
		String currency,
		AlertRule alertRule,
		boolean active,
		int pollIntervalMin,
		Instant lastPolledAt,
		Instant nextPollAt,
		Instant createdAt) {

	public static WatchResponse from(Watch w) {
		return new WatchResponse(
				w.getId(), w.getUserRef(), w.getOrigin(), w.getDestination(), w.getTripType(),
				w.getDepartDateFrom(), w.getDepartDateTo(), w.getReturnDateFrom(), w.getReturnDateTo(),
				w.getPassengers(), w.getCabin(), w.getCurrency(), w.getAlertRule(),
				w.isActive(), w.getPollIntervalMin(), w.getLastPolledAt(), w.getNextPollAt(), w.getCreatedAt());
	}
}
