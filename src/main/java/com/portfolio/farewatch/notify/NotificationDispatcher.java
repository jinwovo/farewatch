package com.portfolio.farewatch.notify;

import com.portfolio.farewatch.domain.Channel;
import com.portfolio.farewatch.domain.DeliveryStatus;
import com.portfolio.farewatch.domain.Notification;
import com.portfolio.farewatch.repo.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Drains the notification outbox: takes PENDING/RETRY rows and delivers each via its
 * channel's {@link NotificationSender}. On success → SENT; on failure → RETRY (with an
 * incremented attempt count) until {@code max-attempts}, then FAILED. Channel-agnostic
 * and at-least-once; the dedup happens upstream (one row per alert+channel).
 */
@Component
public class NotificationDispatcher {

	private final NotificationRepository notifications;
	private final Map<Channel, NotificationSender> senders;
	private final MeterRegistry metrics;
	private final int maxAttempts;

	public NotificationDispatcher(NotificationRepository notifications, List<NotificationSender> senderBeans,
			MeterRegistry metrics,
			@Value("${farewatch.notify.max-attempts:3}") int maxAttempts) {
		this.notifications = notifications;
		this.senders = senderBeans.stream().collect(Collectors.toMap(NotificationSender::channel, s -> s));
		this.metrics = metrics;
		this.maxAttempts = maxAttempts;
	}

	/** Deliver up to {@code max} pending/retry notifications; returns how many were sent. */
	public int dispatch(int max) {
		List<Notification> batch = notifications.findPending(
				List.of(DeliveryStatus.PENDING, DeliveryStatus.RETRY), PageRequest.of(0, max));
		int sent = 0;
		for (Notification n : batch) {
			NotificationSender sender = senders.get(n.getChannel());
			try {
				if (sender == null) {
					throw new IllegalStateException("no sender for channel " + n.getChannel());
				}
				sender.send(n, n.getAlert(), n.getAlert().getWatch());
				n.markSent(Instant.now());
				sent++;
				count(n.getChannel(), "sent");
			} catch (Exception e) {
				if (n.getAttempts() + 1 >= maxAttempts) {
					n.markFailed(e.toString());
					count(n.getChannel(), "failed");
				} else {
					n.markRetry(e.toString());
					count(n.getChannel(), "retry");
				}
			}
			notifications.save(n);
		}
		return sent;
	}

	private void count(Channel channel, String outcome) {
		metrics.counter("farewatch.notifications", "channel", channel.name(), "outcome", outcome).increment();
	}
}
