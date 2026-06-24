package com.portfolio.farewatch.repo;

import com.portfolio.farewatch.domain.Channel;
import com.portfolio.farewatch.domain.DeliveryStatus;
import com.portfolio.farewatch.domain.Notification;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	/** Dedup guard: has this alert already got a notification on this channel? */
	boolean existsByAlert_IdAndChannel(UUID alertId, Channel channel);

	/**
	 * The outbox to drain: not-yet-delivered notifications, oldest first, with their
	 * alert + watch fetched so the sender can run outside a session.
	 */
	@Query("""
			select n from Notification n
			join fetch n.alert a
			join fetch a.watch
			where n.status in :statuses
			order by n.createdAt asc
			""")
	List<Notification> findPending(@Param("statuses") Collection<DeliveryStatus> statuses, Pageable pageable);

	/** Deliveries for one alert (for the alert-history view). */
	List<Notification> findByAlert_IdOrderByChannelAsc(UUID alertId);
}
