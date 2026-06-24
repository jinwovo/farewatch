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
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One delivery of a {@link PriceAlert} over one {@link Channel} — the transactional
 * outbox row. Created (PENDING) in the same transaction as the alert, then a
 * dispatcher sends it with retry/backoff and flips it to SENT or FAILED.
 */
@Entity
@Table(name = "notification")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alert_id", nullable = false)
	private PriceAlert alert;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Channel channel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DeliveryStatus status = DeliveryStatus.PENDING;

	@Column(nullable = false)
	private int attempts = 0;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	@Column(name = "sent_at")
	private Instant sentAt;

	protected Notification() {
	}

	public Notification(PriceAlert alert, Channel channel) {
		this.alert = alert;
		this.channel = channel;
	}

	public void markSent(Instant when) {
		this.status = DeliveryStatus.SENT;
		this.sentAt = when;
		this.lastError = null;
	}

	public void markRetry(String error) {
		this.attempts++;
		this.status = DeliveryStatus.RETRY;
		this.lastError = error;
	}

	public void markFailed(String error) {
		this.attempts++;
		this.status = DeliveryStatus.FAILED;
		this.lastError = error;
	}

	public UUID getId() {
		return id;
	}

	public PriceAlert getAlert() {
		return alert;
	}

	public Channel getChannel() {
		return channel;
	}

	public DeliveryStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public String getLastError() {
		return lastError;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getSentAt() {
		return sentAt;
	}
}
