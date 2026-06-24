package com.portfolio.farewatch.domain;

/** Lifecycle of one notification delivery — matches notification.status CHECK values. */
public enum DeliveryStatus {
	PENDING,
	SENT,
	FAILED,
	RETRY
}
