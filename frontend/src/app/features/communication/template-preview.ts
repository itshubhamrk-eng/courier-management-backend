/**
 * Pure helpers behind the template page's live phone preview — no Angular, so they're cheap to
 * test. The sample values mirror the backend's `TemplateSampleValues`, which is also what Meta
 * is shown at submission, so "what the preview says" and "what Meta reviewed" agree.
 */

/** One run of text in a chat bubble. */
export interface PreviewSegment {
  text: string;
  bold?: boolean;
  italic?: boolean;
  strike?: boolean;
  mono?: boolean;
  link?: boolean;
}

export const DAILY_VARIABLES = [
  'companyName', 'reportDate', 'bookings', 'revenue', 'rechargeAmount', 'rechargeCount',
  'delivered', 'cancelled', 'codToPaySettled', 'inTransit', 'outForDelivery'
];

export const SHIPMENT_VARIABLES = [
  'customerName', 'companyName', 'shipmentNumber', 'trackingNumber', 'pickupLocation',
  'deliveryLocation', 'amount', 'deliveryDate', 'receiverName', 'trackingUrl', 'podUrl'
];

export function variablesFor(eventType: string): string[] {
  return eventType === 'DAILY_BUSINESS_SUMMARY' ? DAILY_VARIABLES : SHIPMENT_VARIABLES;
}

export function sampleValues(companyName?: string | null): Record<string, string> {
  return {
    customerName: 'Rahul Sharma',
    companyName: companyName?.trim() || 'Amazing Logistics',
    shipmentNumber: 'SHP-000123',
    trackingNumber: 'AWB123456789',
    pickupLocation: 'Pune Hub',
    deliveryLocation: 'Mumbai Hub',
    amount: '450.00',
    deliveryDate: '16 Oct 2026',
    receiverName: 'Priya Verma',
    trackingUrl: 'https://example.com/track/AWB123456789',
    podUrl: 'https://example.com/pod/sample.jpg',
    reportDate: '13 Oct 2026',
    bookings: '15',
    revenue: '8,240.00',
    rechargeAmount: '50,000.00',
    rechargeCount: '3',
    delivered: '20',
    cancelled: '2',
    codToPaySettled: '2,120.00',
    inTransit: '8',
    outForDelivery: '5'
  };
}

/** Fills `{{name}}`. An unknown name stays verbatim, exactly like the backend renderer. */
export function fillVariables(content: string, values: Record<string, string>): string {
  return (content ?? '').replace(/\{\{\s*([a-zA-Z0-9_]+)\s*}}/g, (whole, name: string) =>
    Object.prototype.hasOwnProperty.call(values, name) ? values[name] : whole);
}

const TOKEN = new RegExp([
  '(https?:\\/\\/[^\\s]+)',                                      // 1 link
  '```([\\s\\S]+?)```',                                          // 2 monospace
  '(?<![A-Za-z0-9])\\*([^*\\n]+)\\*(?![A-Za-z0-9])',             // 3 bold
  '(?<![A-Za-z0-9])_([^_\\n]+)_(?![A-Za-z0-9])',                 // 4 italic
  '(?<![A-Za-z0-9])~([^~\\n]+)~(?![A-Za-z0-9])'                  // 5 strike
].join('|'), 'g');

/** WhatsApp's own inline formatting: `*bold*`, `_italic_`, `~strike~`, ```mono```, plus links.
 *  Returns plain segments, never HTML, so nothing typed can inject markup. */
export function parseWhatsApp(text: string): PreviewSegment[] {
  const out: PreviewSegment[] = [];
  const source = text ?? '';
  let last = 0;
  for (const m of source.matchAll(TOKEN)) {
    const index = m.index ?? 0;
    if (index > last) out.push({ text: source.slice(last, index) });
    if (m[1] !== undefined) out.push({ text: m[1], link: true });
    else if (m[2] !== undefined) out.push({ text: m[2], mono: true });
    else if (m[3] !== undefined) out.push({ text: m[3], bold: true });
    else if (m[4] !== undefined) out.push({ text: m[4], italic: true });
    else if (m[5] !== undefined) out.push({ text: m[5], strike: true });
    last = index + m[0].length;
  }
  if (last < source.length) out.push({ text: source.slice(last) });
  return out;
}

/** Links only — SMS has no inline formatting. */
export function parsePlain(text: string): PreviewSegment[] {
  const out: PreviewSegment[] = [];
  const source = text ?? '';
  let last = 0;
  for (const m of source.matchAll(/https?:\/\/[^\s]+/g)) {
    const index = m.index ?? 0;
    if (index > last) out.push({ text: source.slice(last, index) });
    out.push({ text: m[0], link: true });
    last = index + m[0].length;
  }
  if (last < source.length) out.push({ text: source.slice(last) });
  return out;
}

// ---------------------------------------------------------------- composing helpers

export const LANGUAGES = [
  { value: 'en_US', label: 'English (US)' },
  { value: 'en', label: 'English' },
  { value: 'hi', label: 'Hindi' },
  { value: 'mr', label: 'Marathi' }
];

export const CATEGORIES = [
  { value: 'UTILITY', label: 'Utility — updates about an order or account' },
  { value: 'MARKETING', label: 'Marketing — offers and promotions' },
  { value: 'AUTHENTICATION', label: 'Authentication — one-time codes' }
];

/** The emoji a courier message actually uses, grouped the way a person looks for them. */
export const EMOJI_GROUPS: { name: string; items: string[] }[] = [
  { name: 'Shipment', items: ['📦', '🚚', '🚛', '🛵', '📍', '🗺️', '🏠', '🏢', '🏭', '🧾', '📝', '🔖'] },
  { name: 'Status', items: ['✅', '☑️', '⏳', '⏰', '🔔', '⚠️', '❌', '🔄', '🆗', '📣', '🚀', '🎉'] },
  { name: 'Money', items: ['💰', '💵', '💳', '🏦', '📊', '📈', '📉', '🪙'] },
  { name: 'People', items: ['🙏', '👍', '😊', '🙂', '👋', '🤝', '❤️', '⭐', '📞', '📱', '✉️', '🌟'] }
];

export type WrapKind = 'bold' | 'italic' | 'strike' | 'mono';
const MARKS: Record<WrapKind, [string, string]> = {
  bold: ['*', '*'], italic: ['_', '_'], strike: ['~', '~'], mono: ['```', '```']
};

/**
 * Wraps the selection in WhatsApp's marks (or, with nothing selected, inserts an empty pair and
 * puts the caret between). Selecting text that is already wrapped removes the marks instead, so
 * the same button toggles. Returns the new text and where to put the selection.
 */
export function wrapSelection(text: string, start: number, end: number, kind: WrapKind):
    { text: string; start: number; end: number } {
  const [open, close] = MARKS[kind];
  const selected = text.slice(start, end);
  if (selected.length >= open.length + close.length && selected.startsWith(open) && selected.endsWith(close)) {
    const inner = selected.slice(open.length, selected.length - close.length);
    return { text: text.slice(0, start) + inner + text.slice(end), start, end: start + inner.length };
  }
  const before = text.slice(Math.max(0, start - open.length), start);
  const after = text.slice(end, end + close.length);
  if (selected.length > 0 && before === open && after === close) {
    return {
      text: text.slice(0, start - open.length) + selected + text.slice(end + close.length),
      start: start - open.length, end: end - open.length
    };
  }
  return {
    text: text.slice(0, start) + open + selected + close + text.slice(end),
    start: start + open.length, end: end + open.length
  };
}

/** Inserts `value` at the selection, returning the text and the caret just after it. */
export function insertAt(text: string, start: number, end: number, value: string):
    { text: string; caret: number } {
  return { text: text.slice(0, start) + value + text.slice(end), caret: start + value.length };
}

const VAR = /\{\{\s*([a-zA-Z0-9_]+)\s*}}/g;

/** Every distinct `{{variable}}` in the given texts, in order of first appearance. */
export function usedVariables(...texts: (string | null | undefined)[]): string[] {
  const seen: string[] = [];
  for (const t of texts) {
    for (const m of (t ?? '').matchAll(VAR)) {
      if (!seen.includes(m[1])) seen.push(m[1]);
    }
  }
  return seen;
}

export interface TemplateCheck { ok: boolean; text: string; }

/**
 * The Meta rules we can judge while typing, so the admin sees "the body ends with a variable"
 * before pressing Submit instead of after. Mirrors the backend's `MetaTemplateConverter` /
 * `TemplateComponentRules` — the backend remains the authority.
 */
export function whatsappChecks(input: {
  name: string; body: string; header: string; footer: string;
  buttons: { type: string; text: string; url?: string | null; phoneNumber?: string | null }[];
  known: string[];
}): TemplateCheck[] {
  const out: TemplateCheck[] = [];
  const body = (input.body ?? '').trim();
  out.push({ ok: /^[a-z0-9_]{1,512}$/.test(input.name ?? ''), text: 'Name uses only lowercase letters, digits and underscores' });
  out.push({ ok: body.length > 0 && body.length <= 1024, text: `Message is 1–1024 characters (now ${body.length})` });
  out.push({ ok: body.length === 0 || (!body.startsWith('{{') && !body.endsWith('}}')), text: 'Message does not start or end with a variable' });
  const unknown = usedVariables(input.body, input.header, ...input.buttons.map((b) => b.url))
    .filter((v) => !input.known.includes(v));
  out.push({ ok: unknown.length === 0, text: unknown.length ? `Unknown variable: ${unknown.map((u) => '{{' + u + '}}').join(', ')}` : 'Every variable is one this app can fill' });
  const header = input.header ?? '';
  out.push({ ok: header.length <= 60 && usedVariables(header).length <= 1, text: 'Header is at most 60 characters with at most one variable' });
  out.push({ ok: (input.footer ?? '').length <= 60 && usedVariables(input.footer).length === 0, text: 'Footer is at most 60 characters and has no variables' });
  const badButton = input.buttons.findIndex((b) => {
    if (!b.text?.trim() || b.text.length > 25) return true;
    if (b.type === 'URL') {
      const url = b.url ?? '';
      const vars = usedVariables(url);
      return !url.startsWith('https://') || vars.length > 1 || (vars.length === 1 && !url.trim().endsWith('}}'));
    }
    return !/^\+[1-9][0-9]{7,14}$/.test((b.phoneNumber ?? '').replace(/[\s-]/g, ''));
  });
  out.push({ ok: badButton < 0, text: badButton < 0 ? 'Buttons are valid' : `Button ${badButton + 1} needs attention (label ≤ 25 characters, https:// link with the variable last, or +country-code number)` });
  return out;
}
