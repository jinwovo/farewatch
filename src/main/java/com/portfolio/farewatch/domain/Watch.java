package com.portfolio.farewatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A user's price watch: a route + (flexible) date window + an alert rule. The
 * hourly sweep (P2) picks up rows whose {@code nextPollAt <= now} and {@code active}.
 */
@Entity
@Table(name = "watch")
public class Watch {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_ref", nullable = false)
	private String userRef;

	@Column(nullable = false, length = 3)
	private String origin;

	@Column(nullable = false, length = 3)
	private String destination;

	@Enumerated(EnumType.STRING)
	@Column(name = "trip_type", nullable = false)
	private TripType tripType;

	@Column(name = "depart_date_from", nullable = false)
	private LocalDate departDateFrom;

	@Column(name = "depart_date_to", nullable = false)
	private LocalDate departDateTo;

	@Column(name = "return_date_from")
	private LocalDate returnDateFrom;

	@Column(name = "return_date_to")
	private LocalDate returnDateTo;

	@Column(name = "depart_time_from")
	private LocalTime departTimeFrom;

	@Column(name = "depart_time_to")
	private LocalTime departTimeTo;

	@Column(nullable = false)
	private int passengers = 1;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Cabin cabin = Cabin.ECONOMY;

	@Column(nullable = false, length = 3)
	private String currency = "KRW";

	@Enumerated(EnumType.STRING)
	@Column(name = "alert_rule", nullable = false)
	private AlertRule alertRule = AlertRule.NEW_LOW;

	@Column(name = "threshold_amount")
	private BigDecimal thresholdAmount;

	@Column(name = "drop_pct")
	private BigDecimal dropPct;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "poll_interval_min", nullable = false)
	private int pollIntervalMin = 60;

	@Column(name = "last_polled_at")
	private Instant lastPolledAt;

	@Column(name = "next_poll_at", nullable = false)
	private Instant nextPollAt = Instant.now();

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private Instant updatedAt;

	public Watch() {
	}

	public UUID getId() {
		return id;
	}

	public String getUserRef() {
		return userRef;
	}

	public void setUserRef(String userRef) {
		this.userRef = userRef;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public TripType getTripType() {
		return tripType;
	}

	public void setTripType(TripType tripType) {
		this.tripType = tripType;
	}

	public LocalDate getDepartDateFrom() {
		return departDateFrom;
	}

	public void setDepartDateFrom(LocalDate departDateFrom) {
		this.departDateFrom = departDateFrom;
	}

	public LocalDate getDepartDateTo() {
		return departDateTo;
	}

	public void setDepartDateTo(LocalDate departDateTo) {
		this.departDateTo = departDateTo;
	}

	public LocalDate getReturnDateFrom() {
		return returnDateFrom;
	}

	public void setReturnDateFrom(LocalDate returnDateFrom) {
		this.returnDateFrom = returnDateFrom;
	}

	public LocalDate getReturnDateTo() {
		return returnDateTo;
	}

	public void setReturnDateTo(LocalDate returnDateTo) {
		this.returnDateTo = returnDateTo;
	}

	public LocalTime getDepartTimeFrom() {
		return departTimeFrom;
	}

	public void setDepartTimeFrom(LocalTime departTimeFrom) {
		this.departTimeFrom = departTimeFrom;
	}

	public LocalTime getDepartTimeTo() {
		return departTimeTo;
	}

	public void setDepartTimeTo(LocalTime departTimeTo) {
		this.departTimeTo = departTimeTo;
	}

	public int getPassengers() {
		return passengers;
	}

	public void setPassengers(int passengers) {
		this.passengers = passengers;
	}

	public Cabin getCabin() {
		return cabin;
	}

	public void setCabin(Cabin cabin) {
		this.cabin = cabin;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public AlertRule getAlertRule() {
		return alertRule;
	}

	public void setAlertRule(AlertRule alertRule) {
		this.alertRule = alertRule;
	}

	public BigDecimal getThresholdAmount() {
		return thresholdAmount;
	}

	public void setThresholdAmount(BigDecimal thresholdAmount) {
		this.thresholdAmount = thresholdAmount;
	}

	public BigDecimal getDropPct() {
		return dropPct;
	}

	public void setDropPct(BigDecimal dropPct) {
		this.dropPct = dropPct;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public int getPollIntervalMin() {
		return pollIntervalMin;
	}

	public void setPollIntervalMin(int pollIntervalMin) {
		this.pollIntervalMin = pollIntervalMin;
	}

	public Instant getLastPolledAt() {
		return lastPolledAt;
	}

	public void setLastPolledAt(Instant lastPolledAt) {
		this.lastPolledAt = lastPolledAt;
	}

	public Instant getNextPollAt() {
		return nextPollAt;
	}

	public void setNextPollAt(Instant nextPollAt) {
		this.nextPollAt = nextPollAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
