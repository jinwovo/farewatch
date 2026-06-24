package com.portfolio.farewatch.web.dto;

import com.portfolio.farewatch.domain.Channel;
import com.portfolio.farewatch.domain.DeliveryStatus;
import com.portfolio.farewatch.domain.Notification;
import java.time.Instant;

public record NotificationResponse(Channel channel, DeliveryStatus status, int attempts, Instant sentAt) {

	public static NotificationResponse from(Notification n) {
		return new NotificationResponse(n.getChannel(), n.getStatus(), n.getAttempts(), n.getSentAt());
	}
}
