/**
 * Pure helpers behind the communication dashboard — no Angular, so they test cheaply. Everything
 * here turns the backend's numbers into what the page shows; none of it invents a figure.
 */
import {
  CommunicationDashboard, CommunicationDayPoint, DashboardPeriod
} from '@core/models/communication.model';

export const PERIODS: { value: DashboardPeriod; label: string }[] = [
  { value: 'TODAY', label: 'Today' },
  { value: 'YESTERDAY', label: 'Yesterday' },
  { value: 'LAST_7_DAYS', label: 'Last 7 days' },
  { value: 'LAST_30_DAYS', label: 'Last 30 days' }
];

export function periodLabel(period: DashboardPeriod | undefined): string {
  return PERIODS.find((p) => p.value === period)?.label ?? 'Today';
}

/** "12.5%" — and an honest dash, not "0%", when nothing was sent to take a share of. */
export function formatRate(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : `${value}%`;
}

/** "3 min ago" / "2 h ago" / "5 d ago" — coarse on purpose; the exact time is in the tooltip. */
export function timeAgo(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return '';
  const seconds = Math.max(0, Math.round((now.getTime() - new Date(iso).getTime()) / 1000));
  if (seconds < 45) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return `${Math.round(hours / 24)} d ago`;
}

/** Chart points: "14 Oct" labels, one series each for sent / delivered / failed. */
export function trendSeries(daily: CommunicationDayPoint[]) {
  const label = (iso: string) => {
    const d = new Date(`${iso}T00:00:00`);
    return `${d.getDate()} ${d.toLocaleString('en-US', { month: 'short' })}`;
  };
  const points = (pick: (d: CommunicationDayPoint) => number) =>
    daily.map((d) => ({ label: label(d.date), value: pick(d) }));
  return [
    { name: 'Sent', points: points((d) => d.sent) },
    { name: 'Delivered', points: points((d) => d.delivered) },
    { name: 'Failed', points: points((d) => d.failed) }
  ];
}

/** A single-day window has one point, which a trend line cannot show. */
export function hasTrend(d: CommunicationDashboard | null): boolean {
  return !!d && d.daily.length > 1 && d.daily.some((p) => p.sent + p.failed > 0);
}

export type ReceiptState = 'live' | 'waiting' | 'idle';

/**
 * Whether Meta's webhook is feeding this dashboard. `live` once any receipt has ever arrived;
 * `waiting` when WhatsApp messages have gone out but no receipt ever came back (webhook not
 * connected — Delivered/Read will sit at 0); `idle` when there is nothing to judge by yet.
 */
export function receiptState(d: CommunicationDashboard | null): ReceiptState {
  if (!d) return 'idle';
  if (d.lastReceiptAt) return 'live';
  const whatsapp = d.channels['WHATSAPP'];
  return (whatsapp?.sent ?? 0) > 0 ? 'waiting' : 'idle';
}

export interface DailySummaryView { tone: 'success' | 'warning' | 'danger' | 'neutral'; headline: string; detail: string; }

/** One honest sentence for the 9:00 AM summary card. */
export function dailySummaryView(d: CommunicationDashboard | null): DailySummaryView {
  const r = d?.dailyReport;
  if (!r) return { tone: 'neutral', headline: 'Not available', detail: '' };
  if (!r.configured) {
    return { tone: 'neutral', headline: 'Switched off',
      detail: 'Add numbers under Channel Settings → WhatsApp → Daily report numbers to get a business summary every morning at 9:00 AM.' };
  }
  const to = `${r.recipients} number${r.recipients === 1 ? '' : 's'}`;
  if (!r.lastStatus) {
    return { tone: 'warning', headline: `On · ${to}`, detail: 'Nothing has been sent yet — the first summary goes out at the next 9:00 AM.' };
  }
  const day = r.lastDate ?? '';
  if (r.lastStatus === 'FAILED') {
    return { tone: 'danger', headline: `Last summary (${day}) failed`, detail: r.lastError ?? 'Meta did not accept it.' };
  }
  if (r.lastReadAt) return { tone: 'success', headline: `Last summary (${day}) was read`, detail: `Sent to ${to}.` };
  if (r.lastStatus === 'DELIVERED') return { tone: 'success', headline: `Last summary (${day}) delivered`, detail: `Sent to ${to}.` };
  return { tone: 'success', headline: `Last summary (${day}) sent`, detail: `Sent to ${to}. Delivery shows once Meta's webhook is connected.` };
}

// ------------------------------------------------------------------------------ cost estimate

/** Money in the company's own rate currency; lakh grouping for rupees, an honest dash for nothing. A currency code
 *  the browser does not know falls back to "1,234.50 XYZ" instead of throwing. */
export function money(value: number | null | undefined, currency = 'INR'): string {
  if (value === null || value === undefined) return '—';
  const code = (currency || 'INR').toUpperCase();
  try {
    return new Intl.NumberFormat(code === 'INR' ? 'en-IN' : 'en-US', {
      style: 'currency', currency: code, minimumFractionDigits: 2, maximumFractionDigits: 2
    }).format(value);
  } catch {
    return `${value.toFixed(2)} ${code}`;
  }
}

export function inr(value: number | null | undefined): string {
  return money(value, 'INR');
}

const CATEGORY_LABEL: Record<string, string> = {
  UTILITY: 'Utility', MARKETING: 'Marketing', AUTHENTICATION: 'Authentication'
};

export interface CostRowView { category: string; messages: number; rate: string; amount: string; }

export interface CostView {
  /** none = no WhatsApp messages in the window; unpriced = messages but no rate entered; partial = some categories unpriced */
  state: 'none' | 'unpriced' | 'partial' | 'ok';
  basis: string;
  rows: CostRowView[];
  total: string;
  hint: string;
}

/**
 * The cost card, in words. It never fabricates a price: with no rate entered it shows the message
 * counts and says where to enter the rates; with some categories unpriced it says the total leaves
 * them out; and it always says whether Delivered or Sent was counted.
 */
export function costView(d: CommunicationDashboard | null): CostView {
  const c = d?.whatsAppCost;
  const cur = c?.currency || 'INR';
  const empty: CostView = { state: 'none', basis: '', rows: [], total: money(0, cur), hint: 'No WhatsApp messages in this period.' };
  if (!c || !c.lines.length) return empty;

  const basis = c.basis === 'DELIVERED'
    ? 'Counting delivered messages — what Meta bills for.'
    : 'Delivery receipts are not connected, so every sent message is counted. The real bill can be lower.';
  const rows = c.lines.map((l) => ({
    category: CATEGORY_LABEL[l.category] ?? l.category,
    messages: l.messages,
    rate: l.rate === null || l.rate === undefined ? 'not set' : money(l.rate, cur),
    amount: l.amount === null || l.amount === undefined ? '—' : money(l.amount, cur)
  }));
  if (!c.anyRateSet) {
    return { state: 'unpriced', basis, rows, total: '—',
      hint: 'Press “Fetch from Meta” to work the rates out from what Meta has actually billed you, or enter them under Channel Settings → WhatsApp. Meta’s prices change by country and date, so the app does not assume any.' };
  }
  if (!c.ratesComplete) {
    return { state: 'partial', basis, rows, total: money(c.total, cur),
      hint: 'The total leaves out categories with no rate entered.' };
  }
  return { state: 'ok', basis, rows, total: money(c.total, cur),
    hint: 'An estimate from your own rates — Meta’s Insights page has the real bill. Some utility messages sent inside a customer’s 24-hour window can be free.' };
}
