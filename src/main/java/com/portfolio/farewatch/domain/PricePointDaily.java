package com.portfolio.farewatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One rolled-up day of a watch's price history (min/max/avg/count), produced by the
 * retention job when raw {@link PricePoint} rows age past the retention window. Keeps
 * the all-time low and long-horizon stats answerable after the raw rows are purged.
 */
@Entity
@Table(name = "price_point_daily")
public class PricePointDaily {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "watch_id", nullable = false)
	private Watch watch;

	@Column(nullable = false)
	private LocalDate day;

	@Column(name = "min_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal minAmount;

	@Column(name = "max_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal maxAmount;

	@Column(name = "avg_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal avgAmount;

	@Column(name = "sample_count", nullable = false)
	private int sampleCount;

	@Column(nullable = false, length = 3)
	private String currency;

	protected PricePointDaily() {
	}

	public UUID getId() {
		return id;
	}

	public Watch getWatch() {
		return watch;
	}

	public LocalDate getDay() {
		return day;
	}

	public BigDecimal getMinAmount() {
		return minAmount;
	}

	public BigDecimal getMaxAmount() {
		return maxAmount;
	}

	public BigDecimal getAvgAmount() {
		return avgAmount;
	}

	public int getSampleCount() {
		return sampleCount;
	}

	public String getCurrency() {
		return currency;
	}
}
