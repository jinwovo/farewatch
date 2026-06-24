package com.portfolio.farewatch.provider;

import com.portfolio.farewatch.domain.Cabin;
import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.domain.Watch;
import java.time.LocalDate;

/** Normalized request a provider answers — derived from a {@link Watch}. */
public record FareQuery(
		String origin,
		String destination,
		TripType tripType,
		LocalDate departDateFrom,
		LocalDate departDateTo,
		LocalDate returnDateFrom,
		LocalDate returnDateTo,
		int passengers,
		Cabin cabin,
		String currency) {

	public static FareQuery from(Watch w) {
		return new FareQuery(
				w.getOrigin(), w.getDestination(), w.getTripType(),
				w.getDepartDateFrom(), w.getDepartDateTo(),
				w.getReturnDateFrom(), w.getReturnDateTo(),
				w.getPassengers(), w.getCabin(), w.getCurrency());
	}
}
