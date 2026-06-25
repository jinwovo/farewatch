package com.portfolio.farewatch.web.dto;

import com.portfolio.farewatch.domain.Airport;
import com.portfolio.farewatch.domain.AlertRule;
import com.portfolio.farewatch.domain.Cabin;
import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.domain.Watch;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
		LocalTime departTimeFrom,
		LocalTime departTimeTo,
		LocalTime returnTimeFrom,
		LocalTime returnTimeTo,
		int passengers,
		Cabin cabin,
		String currency,
		AlertRule alertRule,
		boolean active,
		int pollIntervalMin,
		Instant lastPolledAt,
		Instant nextPollAt,
		Instant createdAt,
		String originKorean,
		String originName,
		String destKorean,
		String destName) {

	public static WatchResponse from(Watch w) {
		return from(w, null, null);
	}

	public static WatchResponse from(Watch w, Airport originAirport, Airport destAirport) {
		return new WatchResponse(
				w.getId(), w.getUserRef(), w.getOrigin(), w.getDestination(), w.getTripType(),
				w.getDepartDateFrom(), w.getDepartDateTo(), w.getReturnDateFrom(), w.getReturnDateTo(),
				w.getDepartTimeFrom(), w.getDepartTimeTo(), w.getReturnTimeFrom(), w.getReturnTimeTo(),
				w.getPassengers(), w.getCabin(), w.getCurrency(), w.getAlertRule(),
				w.isActive(), w.getPollIntervalMin(), w.getLastPolledAt(), w.getNextPollAt(), w.getCreatedAt(),
				originAirport == null ? null : originAirport.getKorean(),
				originAirport == null ? null : originAirport.getName(),
				destAirport == null ? null : destAirport.getKorean(),
				destAirport == null ? null : destAirport.getName());
	}
}
