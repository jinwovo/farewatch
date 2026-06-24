package com.portfolio.farewatch.notify;

import com.portfolio.farewatch.domain.Channel;
import com.portfolio.farewatch.domain.Notification;
import com.portfolio.farewatch.domain.PriceAlert;
import com.portfolio.farewatch.domain.Watch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Push channel. Logs the push for now; a real FCM adapter (token lookup +
 * FirebaseMessaging.send) drops in here without touching the dispatcher.
 */
@Component
public class LogPushSender implements NotificationSender {

	private static final Logger log = LoggerFactory.getLogger(LogPushSender.class);

	@Override
	public Channel channel() {
		return Channel.PUSH;
	}

	@Override
	public void send(Notification notification, PriceAlert alert, Watch watch) {
		log.info("PUSH → {}: {}→{} 최저가 {} {} (rule {})",
				watch.getUserRef(), watch.getOrigin(), watch.getDestination(),
				alert.getNewLow(), watch.getCurrency(), alert.getRule());
	}
}
