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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One observed cheapest offer for a (watch, source) at a point in time. The
 * stream of rows per watch is the price time-series the UI charts and the
 * change-detector (P2) compares against.
 */
@Entity
@Table(name = "price_point")
public class PricePoint {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "watch_id", nullable = false)
	private Watch watch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_id", nullable = false)
	private FareSource source;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "depart_date", nullable = false)
	private LocalDate departDate;

	@Column(name = "return_date")
	private LocalDate returnDate;

	@Column(name = "deep_link", columnDefinition = "text")
	private String deepLink;

	@CreationTimestamp
	@Column(name = "observed_at", updatable = false)
	private Instant observedAt;

	protected PricePoint() {
	}

	public PricePoint(Watch watch, FareSource source, BigDecimal amount, String currency,
			LocalDate departDate, LocalDate returnDate, String deepLink) {
		this.watch = watch;
		this.source = source;
		this.amount = amount;
		this.currency = currency;
		this.departDate = departDate;
		this.returnDate = returnDate;
		this.deepLink = deepLink;
	}

	public UUID getId() {
		return id;
	}

	public Watch getWatch() {
		return watch;
	}

	public FareSource getSource() {
		return source;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public LocalDate getDepartDate() {
		return departDate;
	}

	public LocalDate getReturnDate() {
		return returnDate;
	}

	public String getDeepLink() {
		return deepLink;
	}

	public Instant getObservedAt() {
		return observedAt;
	}
}
