package com.portfolio.farewatch.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One day in the cheapest-date heatmap: the lowest price observed for that departure date. */
public record CalendarCell(LocalDate date, BigDecimal lowestAmount, String currency) {
}
