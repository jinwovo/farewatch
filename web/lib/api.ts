// Typed client for the farewatch REST API. Calls are same-origin (/api/*) and
// proxied to the Spring backend by next.config.mjs rewrites.

export type TripType = 'ONE_WAY' | 'ROUND_TRIP';
export type Cabin = 'ECONOMY' | 'PREMIUM_ECONOMY' | 'BUSINESS' | 'FIRST';
export type AlertRule = 'NEW_LOW' | 'BELOW_THRESHOLD' | 'DROP_PCT';

export interface Airport {
  iata: string;
  name: string;
  municipality: string | null;
  country: string;
  large: boolean;
}

export interface NearbyAirport extends Airport {
  distanceKm: number;
}

export interface Watch {
  id: string;
  userRef: string;
  origin: string;
  destination: string;
  tripType: TripType;
  departDateFrom: string;
  departDateTo: string;
  returnDateFrom?: string | null;
  returnDateTo?: string | null;
  departTimeFrom?: string | null;
  departTimeTo?: string | null;
  returnTimeFrom?: string | null;
  returnTimeTo?: string | null;
  passengers: number;
  cabin: Cabin;
  currency: string;
  alertRule: AlertRule;
  active: boolean;
  pollIntervalMin: number;
  lastPolledAt?: string | null;
  nextPollAt: string;
  createdAt: string;
}

export interface PricePoint {
  id: string;
  source: string;
  amount: number;
  currency: string;
  departDate: string;
  returnDate?: string | null;
  deepLink?: string | null;
  observedAt: string;
}

export interface PollResult {
  watchId: string;
  polledAt: string;
  newPrices: PricePoint[];
  lowestAmount?: number | null;
  lowestCurrency?: string | null;
  lowestDepartDate?: string | null;
  lowestDeepLink?: string | null;
  newLow: boolean;
}

export interface CalendarCell {
  date: string;
  lowestAmount: number;
  currency: string;
}

export type Channel = 'PUSH' | 'EMAIL';
export type DeliveryStatus = 'PENDING' | 'SENT' | 'FAILED' | 'RETRY';

export interface NotificationDelivery {
  channel: Channel;
  status: DeliveryStatus;
  attempts: number;
  sentAt?: string | null;
}

export interface Alert {
  id: string;
  rule: AlertRule;
  previousLow?: number | null;
  newLow: number;
  mistakeFare: boolean;
  createdAt: string;
  notifications: NotificationDelivery[];
}

export type WeatherSource = 'FORECAST' | 'CLIMATE_NORMAL';

export interface WeatherEstimate {
  date: string;
  tempMaxC: number | null;
  tempMinC: number | null;
  precipProbPct: number | null;
  source: WeatherSource;
}

export type Recommendation = 'BUY' | 'WAIT' | 'CONSIDER' | 'NO_DATA';

export interface BuySignal {
  recommendation: Recommendation;
  score: number;
  currentAmount: number;
  lowestAmount: number;
  percentile: number;
  trendPct: number;
  volatilityPct: number;
  daysToDeparture: number;
  reason: string;
}

export interface CreateWatchInput {
  userRef: string;
  origin: string;
  destination: string;
  tripType: TripType;
  departDateFrom: string;
  departDateTo: string;
  returnDateFrom?: string;
  returnDateTo?: string;
  departTimeFrom?: string;
  departTimeTo?: string;
  returnTimeFrom?: string;
  returnTimeTo?: string;
  passengers?: number;
  cabin?: Cabin;
  currency?: string;
  alertRule?: AlertRule;
  pollIntervalMin?: number;
}

async function unwrap<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `${res.status} ${res.statusText}`);
  }
  return res.status === 204 ? (undefined as T) : (res.json() as Promise<T>);
}

export const api = {
  searchAirports: (q: string): Promise<Airport[]> =>
    fetch(`/api/airports?q=${encodeURIComponent(q)}`, { cache: 'no-store' }).then((r) => unwrap<Airport[]>(r)),
  nearbyAirports: (iata: string): Promise<NearbyAirport[]> =>
    fetch(`/api/airports/${iata}/nearby?limit=5`, { cache: 'no-store' }).then((r) => unwrap<NearbyAirport[]>(r)),
  listWatches: (userRef?: string): Promise<Watch[]> =>
    fetch(`/api/watches${userRef ? `?userRef=${encodeURIComponent(userRef)}` : ''}`, { cache: 'no-store' })
      .then((r) => unwrap<Watch[]>(r)),
  getWatch: (id: string): Promise<Watch> =>
    fetch(`/api/watches/${id}`, { cache: 'no-store' }).then((r) => unwrap<Watch>(r)),
  createWatch: (body: CreateWatchInput): Promise<Watch> =>
    fetch('/api/watches', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }).then((r) => unwrap<Watch>(r)),
  deleteWatch: (id: string): Promise<void> =>
    fetch(`/api/watches/${id}`, { method: 'DELETE' }).then((r) => unwrap<void>(r)),
  pollWatch: (id: string): Promise<PollResult> =>
    fetch(`/api/watches/${id}/poll`, { method: 'POST' }).then((r) => unwrap<PollResult>(r)),
  getPrices: (id: string): Promise<PricePoint[]> =>
    fetch(`/api/watches/${id}/prices`, { cache: 'no-store' }).then((r) => unwrap<PricePoint[]>(r)),
  getCalendar: (id: string): Promise<CalendarCell[]> =>
    fetch(`/api/watches/${id}/calendar`, { cache: 'no-store' }).then((r) => unwrap<CalendarCell[]>(r)),
  getAlerts: (id: string): Promise<Alert[]> =>
    fetch(`/api/watches/${id}/alerts`, { cache: 'no-store' }).then((r) => unwrap<Alert[]>(r)),
  getWeather: (id: string): Promise<WeatherEstimate[]> =>
    fetch(`/api/watches/${id}/weather`, { cache: 'no-store' }).then((r) => unwrap<WeatherEstimate[]>(r)),
  getSignal: (id: string): Promise<BuySignal> =>
    fetch(`/api/watches/${id}/signal`, { cache: 'no-store' }).then((r) => unwrap<BuySignal>(r)),
};
