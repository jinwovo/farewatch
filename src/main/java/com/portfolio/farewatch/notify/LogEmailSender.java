package com.portfolio.farewatch.notify;

import com.portfolio.farewatch.domain.Channel;
import com.portfolio.farewatch.domain.Notification;
import com.portfolio.farewatch.domain.PriceAlert;
import com.portfolio.farewatch.domain.Watch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Email channel. Logs the email for now; a real SMTP / provider adapter
 * (JavaMailSender / SES) drops in here without touching the dispatcher.
 */
@Component
public class LogEmailSender implements NotificationSender {

	private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

	@Override
	public Channel channel() {
		return Channel.EMAIL;
	}

	@Override
	public void send(Notification notification, PriceAlert alert, Watch watch) {
		log.info("EMAIL → {}: [farewatch] {}→{} 새 최저가 {} {}",
				watch.getUserRef(), watch.getOrigin(), watch.getDestination(),
				alert.getNewLow(), watch.getCurrency());
	}
}
