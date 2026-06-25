package com.portfolio.farewatch.service;

import com.portfolio.farewatch.domain.PricePoint;
import com.portfolio.farewatch.domain.Watch;
import com.portfolio.farewatch.repo.PricePointRepository;
import com.portfolio.farewatch.repo.WatchRepository;
import com.portfolio.farewatch.web.dto.BuySignal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes a {@link BuySignal} from a watch's price history. The recommendation is a
 * small, explainable rule set over four factors so the reason is always defensible:
 * percentile (is it cheap vs this route's own history), trend (rising/falling),
 * volatility, and days-to-departure (urgency).
 */
@Service
public class BuySignalService {

	/** Stats run over the most recent observations, not the whole history (scales to long series). */
	private static final int STAT_WINDOW = 200;

	private final WatchRepository watches;
	private final PricePointRepository pricePoints;

	public BuySignalService(WatchRepository watches, PricePointRepository pricePoints) {
		this.watches = watches;
		this.pricePoints = pricePoints;
	}

	@Transactional(readOnly = true)
	public BuySignal signalFor(UUID watchId) {
		Watch w = watches.findById(watchId)
				.orElseThrow(() -> new NoSuchElementException("watch not found: " + watchId));
		// Bounded window (newest first) → reversed to oldest-first so a[n-1] is the latest.
		List<PricePoint> pts = pricePoints
				.findByWatch_IdOrderByObservedAtDesc(watchId, PageRequest.of(0, STAT_WINDOW))
				.reversed();
		long days = ChronoUnit.DAYS.between(LocalDate.now(), w.getDepartDateFrom());

		if (pts.size() < 3) {
			double cur = pts.isEmpty() ? 0 : pts.get(pts.size() - 1).getAmount().doubleValue();
			return new BuySignal("NO_DATA", 50, cur, cur, 0, 0, 0, days,
					"관측 데이터가 아직 적어 신호를 내기 어려워요. 며칠 더 추적해보세요.");
		}

		double[] a = pts.stream().mapToDouble(p -> p.getAmount().doubleValue()).toArray();
		int n = a.length;
		double current = a[n - 1];
		// All-time low via the indexed (watch_id, amount) lookup — not a JVM scan of the window.
		double lowest = pricePoints.findFirstByWatch_IdOrderByAmountAscObservedAtAsc(watchId)
				.map(p -> p.getAmount().doubleValue()).orElse(current);

		double below = 0;
		for (double v : a) {
			if (v < current) {
				below++;
			}
		}
		double percentile = below / n * 100.0;

		double mean = mean(a, 0, n);
		double sd = stddev(a, mean);
		double volatility = mean > 0 ? sd / mean * 100.0 : 0;

		int win = Math.min(n, 20);
		double recent = mean(a, n - win, n);
		int win2 = Math.min(n - win, win);
		double prior = win2 > 0 ? mean(a, n - win - win2, n - win) : recent;
		double trend = prior > 0 ? (recent - prior) / prior * 100.0 : 0;

		boolean cheap = percentile <= 25;
		boolean expensive = percentile >= 60;
		boolean falling = trend < -1;
		boolean rising = trend > 1;
		boolean soon = days <= 10;

		String rec;
		String reason;
		if (cheap && !falling) {
			rec = "BUY";
			reason = String.format("지금 가격은 %s이고 추세도 %s — 좋은 타이밍이에요.", cheapness(percentile), rising ? "상승세" : "보합");
		} else if (soon && percentile <= 50) {
			rec = "BUY";
			reason = String.format("출발 %d일 전이고 가격도 %s — 보통 임박할수록 오르니 지금 잡는 걸 추천해요.", Math.max(0, days), cheapness(percentile));
		} else if (expensive && falling && days > 14) {
			rec = "WAIT";
			reason = String.format("%s인데 하락 추세 — 며칠 더 지켜봐도 좋아요.", cheapness(percentile));
		} else {
			rec = "CONSIDER";
			reason = String.format("%s — 급하면 지금, 여유 있으면 조금 더 지켜보세요.", cheapness(percentile));
		}

		int score = clamp((int) Math.round(0.7 * (100 - percentile)
				+ (rising ? 8 : 0) + (soon ? 10 : 0) - (falling ? 6 : 0)), 0, 100);

		return new BuySignal(rec, score, current, lowest, round1(percentile), round1(trend), round1(volatility), days, reason);
	}

	private static double mean(double[] a, int from, int to) {
		double s = 0;
		for (int i = from; i < to; i++) {
			s += a[i];
		}
		return (to - from) > 0 ? s / (to - from) : 0;
	}

	private static double stddev(double[] a, double mean) {
		double s = 0;
		for (double v : a) {
			s += (v - mean) * (v - mean);
		}
		return Math.sqrt(s / a.length);
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static double round1(double v) {
		return Math.round(v * 10.0) / 10.0;
	}

	/** Qualitative cheapness from the percentile (% of past prices cheaper than now). */
	private static String cheapness(double percentile) {
		if (percentile <= 10) {
			return "역대 최저가 수준";
		}
		if (percentile <= 30) {
			return "꽤 싼 편";
		}
		if (percentile <= 60) {
			return "중간 가격대";
		}
		return "비싼 편";
	}
}
