package com.portfolio.farewatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.portfolio.farewatch.domain.AlertRule;
import com.portfolio.farewatch.domain.Channel;
import com.portfolio.farewatch.domain.DeliveryStatus;
import com.portfolio.farewatch.domain.FareSource;
import com.portfolio.farewatch.domain.Notification;
import com.portfolio.farewatch.domain.PriceAlert;
import com.portfolio.farewatch.domain.PricePoint;
import com.portfolio.farewatch.domain.TripType;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.notify.NotificationDispatcher;
import com.portfolio.farewatch.notify.NotificationSender;
import com.portfolio.farewatch.repo.FareSourceRepository;
import com.portfolio.farewatch.repo.NotificationRepository;
import com.portfolio.farewatch.repo.PriceAlertRepository;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import com.portfolio.farewatch.service.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Notification outbox: retry/backoff bookkeeping and idempotent creation. Uses a
 * dispatcher built with a deliberately failing sender to drive the RETRY → FAILED
 * transitions deterministically.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NotificationDispatchTest {

	@Autowired
	WatchRepository watches;
	@Autowired
	FareSourceRepository fareSources;
	@Autowired
	PricePointRepository pricePoints;
	@Autowired
	PriceAlertRepository priceAlerts;
	@Autowired
	NotificationRepository notifications;
	@Autowired
	NotificationService notificationService;

	@Test
	void retries_then_fails_after_max_attempts() {
		Notification n = notifications.save(new Notification(newAlert(), Channel.PUSH));
		NotificationDispatcher dispatcher = new NotificationDispatcher(
				notifications, List.of(alwaysFails(Channel.PUSH)), 2);

		dispatcher.dispatch(10);
		Notification afterFirst = notifications.findById(n.getId()).orElseThrow();
		assertEquals(DeliveryStatus.RETRY, afterFirst.getStatus());
		assertEquals(1, afterFirst.getAttempts());

		dispatcher.dispatch(10);
		Notification afterSecond = notifications.findById(n.getId()).orElseThrow();
		assertEquals(DeliveryStatus.FAILED, afterSecond.getStatus()); // 2nd attempt hits max → FAILED
		assertEquals(2, afterSecond.getAttempts());
	}

	@Test
	void createForAlert_is_idempotent() {
		PriceAlert alert = newAlert();
		notificationService.createForAlert(alert, alert.getWatch());
		notificationService.createForAlert(alert, alert.getWatch()); // again → no duplicates
		assertEquals(2, notifications.findByAlert_IdOrderByChannelAsc(alert.getId()).size()); // PUSH + EMAIL only
	}

	private PriceAlert newAlert() {
		Watch w = watches.save(watch());
		FareSource source = fareSources.findByCode("SIMULATOR").orElseThrow();
		BigDecimal amount = new BigDecimal("123400.00");
		PricePoint pp = pricePoints.save(new PricePoint(
				w, source, amount, "KRW", LocalDate.now().plusDays(30), null, "https://book.example"));
		return priceAlerts.save(new PriceAlert(
				w, pp, AlertRule.NEW_LOW, null, amount, "dedup-" + UUID.randomUUID()));
	}

	private Watch watch() {
		Watch w = new Watch();
		w.setUserRef("notify-" + UUID.randomUUID()); // unique → no uq_watch_dedupe clash across methods
		w.setOrigin("ICN");
		w.setDestination("SIN");
		w.setTripType(TripType.ONE_WAY);
		w.setDepartDateFrom(LocalDate.now().plusDays(30));
		w.setDepartDateTo(LocalDate.now().plusDays(30));
		w.setNextPollAt(Instant.now());
		return w;
	}

	private NotificationSender alwaysFails(Channel channel) {
		return new NotificationSender() {
			@Override
			public Channel channel() {
				return channel;
			}

			@Override
			public void send(Notification notification, PriceAlert alert, Watch watch) {
				throw new RuntimeException("delivery boom");
			}
		};
	}
}
