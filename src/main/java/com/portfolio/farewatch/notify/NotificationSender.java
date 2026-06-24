package com.portfolio.farewatch.notify;

import com.portfolio.farewatch.domain.Channel;
import com.portfolio.farewatch.domain.Notification;
import com.portfolio.farewatch.domain.PriceAlert;
import com.portfolio.farewatch.domain.Watch;

/**
 * Sends one notification over one channel. Implementations are the only place that
 * knows how to actually deliver (FCM, SMTP, …); the dispatcher (outbox + retry) is
 * channel-agnostic. Throwing signals a delivery failure → retry/backoff.
 */
public interface NotificationSender {

	Channel channel();

	void send(Notification notification, PriceAlert alert, Watch watch) throws Exception;
}
