package com.portfolio.farewatch.domain;

/** When to fire an alert — matches watch.alert_rule CHECK values. */
public enum AlertRule {
	/** Fire whenever a new all-time-low (within lookback) is observed. */
	NEW_LOW,
	/** Fire when the price drops below watch.threshold_amount. */
	BELOW_THRESHOLD,
	/** Fire when the price drops by at least watch.drop_pct percent. */
	DROP_PCT
}
