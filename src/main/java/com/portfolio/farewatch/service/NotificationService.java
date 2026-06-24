package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.Channel;
import com.portfolio.farewatch.domain.Notification;
import com.portfolio.farewatch.domain.PriceAlert;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.NotificationRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Transactional outbox: when an alert fires, write one PENDING {@link Notification}
 * per channel in the SAME transaction as the alert. Idempotent (deduped per
 * alert+channel), so a re-fired or retried alert never doubles up. A separate
 * dispatcher then delivers them. Runs inside the caller's (poll) transaction.
 */
@Service
public class NotificationService {

	private static final List<Channel> CHANNELS = List.of(Channel.PUSH, Channel.EMAIL);

	private final NotificationRepository notifications;

	public NotificationService(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	public void createForAlert(PriceAlert alert, Watch watch) {
		for (Channel channel : CHANNELS) {
			if (!notifications.existsByAlert_IdAndChannel(alert.getId(), channel)) {
				notifications.save(new Notification(alert, channel));
			}
		}
	}
}
