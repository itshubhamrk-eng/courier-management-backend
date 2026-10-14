import { describe, expect, it } from 'vitest';
import { CommunicationDashboard } from '@core/models/communication.model';
import {
  costView, dailySummaryView, formatRate, hasTrend, inr, money, periodLabel, receiptState, timeAgo, trendSeries
} from './dashboard-view';

const base = (over: Partial<CommunicationDashboard> = {}): CommunicationDashboard => ({
  totalSent: 0, totalDelivered: 0, totalFailed: 0, totalPending: 0, channels: {}, period: 'TODAY',
  totalRead: 0, daily: [], recentFailures: [], ...over
});

describe('dashboard view helpers', () => {
  it('labels periods and falls back to Today', () => {
    expect(periodLabel('LAST_7_DAYS')).toBe('Last 7 days');
    expect(periodLabel(undefined)).toBe('Today');
  });

  it('shows a dash, never 0%, when nothing was sent', () => {
    expect(formatRate(null)).toBe('—');
    expect(formatRate(undefined)).toBe('—');
    expect(formatRate(66.7)).toBe('66.7%');
    expect(formatRate(0)).toBe('0%');
  });

  it('words elapsed time coarsely', () => {
    const now = new Date('2026-10-14T12:00:00Z');
    expect(timeAgo(null, now)).toBe('');
    expect(timeAgo('2026-10-14T11:59:50Z', now)).toBe('just now');
    expect(timeAgo('2026-10-14T11:55:00Z', now)).toBe('5 min ago');
    expect(timeAgo('2026-10-14T09:00:00Z', now)).toBe('3 h ago');
    expect(timeAgo('2026-10-11T12:00:00Z', now)).toBe('3 d ago');
    expect(timeAgo('2026-10-14T13:00:00Z', now)).toBe('just now'); // future clock skew never goes negative
  });

  it('builds sent / delivered / failed series with short date labels', () => {
    const s = trendSeries([
      { date: '2026-10-13', sent: 2, delivered: 1, failed: 0 },
      { date: '2026-10-14', sent: 5, delivered: 4, failed: 1 }
    ]);
    expect(s.map((x) => x.name)).toEqual(['Sent', 'Delivered', 'Failed']);
    expect(s[0].points).toEqual([{ label: '13 Oct', value: 2 }, { label: '14 Oct', value: 5 }]);
    expect(s[2].points.map((p) => p.value)).toEqual([0, 1]);
  });

  it('only offers a trend for several days that actually had traffic', () => {
    expect(hasTrend(null)).toBe(false);
    expect(hasTrend(base({ daily: [{ date: '2026-10-14', sent: 3, delivered: 0, failed: 0 }] }))).toBe(false);
    const quiet = [0, 1, 2].map((i) => ({ date: `2026-10-1${i}`, sent: 0, delivered: 0, failed: 0 }));
    expect(hasTrend(base({ daily: quiet }))).toBe(false);
    expect(hasTrend(base({ daily: [...quiet.slice(0, 2), { date: '2026-10-14', sent: 1, delivered: 0, failed: 0 }] }))).toBe(true);
  });

  describe('receiptState', () => {
    it('is live as soon as any receipt has ever arrived', () => {
      expect(receiptState(base({ lastReceiptAt: '2026-10-14T10:00:00Z' }))).toBe('live');
    });
    it('is waiting when WhatsApp sent messages but no receipt ever came back', () => {
      expect(receiptState(base({ channels: { WHATSAPP: { sent: 2, delivered: 0, failed: 0, pending: 0, cancelled: 0 } } }))).toBe('waiting');
    });
    it('is idle with nothing to judge by', () => {
      expect(receiptState(null)).toBe('idle');
      expect(receiptState(base())).toBe('idle');
      expect(receiptState(base({ channels: { SMS: { sent: 9, delivered: 0, failed: 0, pending: 0, cancelled: 0 } } }))).toBe('idle');
    });
  });

  describe('dailySummaryView', () => {
    const report = (over: object) => base({ dailyReport: { configured: true, recipients: 2, ...over } });

    it('says switched off, with how to turn it on', () => {
      const v = dailySummaryView(base({ dailyReport: { configured: false, recipients: 0 } }));
      expect(v.headline).toBe('Switched off');
      expect(v.detail).toContain('Daily report numbers');
    });
    it('says nothing has gone out yet', () => {
      expect(dailySummaryView(report({})).headline).toBe('On · 2 numbers');
      expect(dailySummaryView(base({ dailyReport: { configured: true, recipients: 1 } })).headline).toBe('On · 1 number');
    });
    it('reports a failure with its reason in the danger tone', () => {
      const v = dailySummaryView(report({ lastStatus: 'FAILED', lastDate: '2026-10-13', lastError: 'Template not approved' }));
      expect(v.tone).toBe('danger');
      expect(v.detail).toBe('Template not approved');
    });
    it('distinguishes read, delivered and merely sent', () => {
      expect(dailySummaryView(report({ lastStatus: 'DELIVERED', lastDate: 'd', lastReadAt: '2026-10-14T04:00:00Z' })).headline).toContain('was read');
      expect(dailySummaryView(report({ lastStatus: 'DELIVERED', lastDate: 'd' })).headline).toContain('delivered');
      const sent = dailySummaryView(report({ lastStatus: 'SENT', lastDate: 'd' }));
      expect(sent.headline).toContain('sent');
      expect(sent.detail).toContain('webhook');
    });
    it('handles a missing dashboard', () => {
      expect(dailySummaryView(null).tone).toBe('neutral');
    });
  });

  describe('money', () => {
    it('uses lakh grouping for rupees and western grouping for other currencies', () => {
      expect(money(1234567.5, 'INR')).toBe('₹12,34,567.50');
      expect(money(1234567.5, 'USD')).toBe('$1,234,567.50');
      expect(money(5)).toBe('₹5.00');
      expect(money(null, 'USD')).toBe('—');
    });

    it('survives a currency code the browser does not know', () => {
      expect(money(3.5, 'ZZZZ')).toBe('3.50 ZZZZ');
      expect(money(3.5, '')).toBe('₹3.50');
    });
  });

  describe('costView', () => {
    const cost = (over: object) => base({ whatsAppCost: { basis: 'DELIVERED', anyRateSet: true, ratesComplete: true, lines: [], total: 0, currency: 'INR', ...over } });
    const line = (category: string, messages: number, rate: number | null, amount: number | null) => ({ category, messages, rate, amount });

    it('formats rupees with lakh grouping and a dash for nothing', () => {
      expect(inr(122.8)).toBe('₹122.80');
      expect(inr(1234567.5)).toBe('₹12,34,567.50');
      expect(inr(null)).toBe('—');
    });

    it('says so when there were no WhatsApp messages', () => {
      expect(costView(null).state).toBe('none');
      expect(costView(cost({})).state).toBe('none');
      expect(costView(cost({})).hint).toContain('No WhatsApp messages');
    });

    it('never prices anything when no rate has been entered', () => {
      const v = costView(cost({ anyRateSet: false, ratesComplete: false, lines: [line('UTILITY', 40, null, null)] }));
      expect(v.state).toBe('unpriced');
      expect(v.total).toBe('—');
      expect(v.rows[0]).toEqual({ category: 'Utility', messages: 40, rate: 'not set', amount: '—' });
      expect(v.hint).toContain('Channel Settings');
      expect(v.hint).toContain('Fetch from Meta');
    });

    it('prices each category and totals when every rate is set', () => {
      const v = costView(cost({ total: 122.8, lines: [line('UTILITY', 1000, 0.115, 115), line('MARKETING', 10, 0.78, 7.8)] }));
      expect(v.state).toBe('ok');
      expect(v.total).toBe('₹122.80');
      expect(v.rows.map((r) => r.amount)).toEqual(['₹115.00', '₹7.80']);
      expect(v.rows[0].rate).toBe('₹0.115'.replace('₹0.115', inr(0.115)));
    });

    it('flags a total that leaves out unpriced categories', () => {
      const v = costView(cost({ ratesComplete: false, total: 3, lines: [line('UTILITY', 3, 1, 3), line('MARKETING', 2, null, null)] }));
      expect(v.state).toBe('partial');
      expect(v.hint).toContain('leaves out');
      expect(v.rows[1].rate).toBe('not set');
    });

    it('prices in the account currency, not always rupees', () => {
      const v = costView(cost({ currency: 'USD', total: 1.5, lines: [line('UTILITY', 10, 0.15, 1.5)] }));
      expect(v.total).toBe('$1.50');
      expect(v.rows[0].rate).toBe('$0.15');
    });

    it('is honest about whether delivered or merely sent messages were counted', () => {
      const delivered = costView(cost({ basis: 'DELIVERED', lines: [line('UTILITY', 1, 1, 1)] }));
      const sent = costView(cost({ basis: 'SENT', lines: [line('UTILITY', 1, 1, 1)] }));
      expect(delivered.basis).toContain('delivered');
      expect(sent.basis).toContain('every sent message');
    });
  });
});
