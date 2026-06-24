package com.portfolio.farewatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A detected trigger (new low / threshold crossed / drop %). {@code dedupKey} makes
 * alerting idempotent: the same drop never fires twice across retries or instances.
 * P3 turns these rows into delivered notifications (FCM / email).
 */
@Entity
@Table(name = "price_alert")
public class PriceAlert {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "watch_id", nullable = false)
	private Watch watch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "triggering_price_point_id", nullable = false)
	private PricePoint triggeringPricePoint;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AlertRule rule;

	@Column(name = "previous_low")
	private BigDecimal previousLow;

	@Column(name = "new_low", nullable = false)
	private BigDecimal newLow;

	@Column(name = "dedup_key", nullable = false, unique = true)
	private String dedupKey;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	protected PriceAlert() {
	}

	public PriceAlert(Watch watch, PricePoint triggeringPricePoint, AlertRule rule,
			BigDecimal previousLow, BigDecimal newLow, String dedupKey) {
		this.watch = watch;
		this.triggeringPricePoint = triggeringPricePoint;
		this.rule = rule;
		this.previousLow = previousLow;
		this.newLow = newLow;
		this.dedupKey = dedupKey;
	}

	public UUID getId() {
		return id;
	}

	public Watch getWatch() {
		return watch;
	}

	public PricePoint getTriggeringPricePoint() {
		return triggeringPricePoint;
	}

	public AlertRule getRule() {
		return rule;
	}

	public BigDecimal getPreviousLow() {
		return previousLow;
	}

	public BigDecimal getNewLow() {
		return newLow;
	}

	public String getDedupKey() {
		return dedupKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
