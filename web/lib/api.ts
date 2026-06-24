// Typed client for the farewatch REST API. Calls are same-origin (/api/*) and
// proxied to the Spring backend by next.config.mjs rewrites.

export type TripType = 'ONE_WAY' | 'ROUND_TRIP';
export type Cabin = 'ECONOMY' | 'PREMIUM_ECONOMY' | 'BUSINESS' | 'FIRST';
export type AlertRule = 'NEW_LOW' | 'BELOW_THRESHOLD' | 'DROP_PCT';

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

export interface CreateWatchInput {
  userRef: string;
  origin: string;
  destination: string;
  tripType: TripType;
  departDateFrom: string;
  departDateTo: string;
  returnDateFrom?: string;
  returnDateTo?: string;
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
};
