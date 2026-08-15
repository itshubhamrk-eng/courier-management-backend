import { NavTarget } from '../navigation/nav-flatten.util';

/**
 * Rule-based command router for the dashboard's AI assistant bar — turns a typed or
 * spoken sentence ("track SHP-000123", "check rate", "inscan order", "dispatch") into a
 * navigation, using the same approach as `voice-booking.util.ts`: keyword/alias
 * matching against real app data, no LLM call. The "real app data" here is the
 * signed-in user's own permission-filtered nav tree (`flattenNavTargets`), so the
 * assistant only ever offers to go somewhere that user's sidebar already shows —
 * exactly the generic "or other thing" coverage a hand-written intent list can't keep
 * up with as new modules ship.
 */
export interface AiCommandMatch {
  route: string;
  label: string;
  queryParams?: Record<string, string>;
}

/** A tracking token looks like `SHP-000123` / `AWB123456` (letters + digits, optionally
 *  hyphenated) or a bare run of 8+ digits — long enough not to collide with a 6-digit
 *  pincode or a short quantity typed elsewhere in the same sentence. */
const TRACK_TOKEN = /\b([A-Za-z]{2,6}-?\d{4,})\b|\b(\d{8,})\b/;

/** Nav ids (see `navigation.config.ts`) mapped to extra phrases that don't literally
 *  share a word with their real nav title — "check rate" has to reach the "Calculator"
 *  leaf under Rate Master somehow. English + common Hindi/Marathi phrasing, same
 *  best-effort-not-exhaustive convention `voice-booking.util.ts` already documents. */
const ALIASES: Record<string, string[]> = {
  'track': ['track', 'lr', 'awb', 'consignment', 'status', 'ट्रैक', 'ट्रॅक', 'स्थिति'],
  'rate-calculator': ['rate', 'check rate', 'calculate rate', 'freight rate', 'रेट', 'भाड़ा', 'दर'],
  'manifest': ['loading sheet', 'outscan', 'out scan', 'लोडिंग शीट', 'आउट स्कैन', 'आउट स्कॅन'],
  'receive': ['inscan', 'in scan', 'receive', 'इन स्कैन', 'इन स्कॅन'],
  'dispatch': ['dispatch', 'thc', 'trip hire challan', 'डिस्पैच', 'डिस्पॅच'],
  'booking': ['book', 'book shipment', 'new shipment', 'create shipment', 'बुक', 'बुकिंग'],
  'shipment-search': ['search shipment', 'find shipment'],
  'branch-wallet': ['wallet', 'wallet balance', 'check wallet', 'वॉलेट', 'बॅलन्स'],
  'wallet-transactions': ['wallet transactions', 'transactions'],
  'topup-requests': ['topup', 'top up', 'recharge requests'],
  'customers': ['customer', 'customers'],
  'branches': ['branch list', 'branches'],
  'users': ['staff', 'users'],
  'pending-delivery': ['pending delivery'],
  'out-for-delivery': ['out for delivery'],
  'delivery': ['mark delivered', 'delivered']
};

function condense(s: string): string {
  return s.toLowerCase().replace(/[\s\-_]+/g, '');
}

const STOPWORDS = new Set([
  'the', 'a', 'an', 'please', 'pls', 'my', 'me', 'for', 'of', 'on', 'to', 'check', 'show',
  'open', 'go', 'goto', 'order', 'the', 'is', 'please'
]);

function significantWords(text: string): string[] {
  return text.toLowerCase().split(/[^a-zऀ-ॿ]+/i).filter((w) => w.length > 1 && !STOPWORDS.has(w));
}

/** Extracts an AWB/shipment-number-looking token, if the text has one — a bare id typed
 *  or spoken alone is itself the intent ("SHP-000123" means "track this"), no "track"
 *  keyword required. */
function extractTrackToken(text: string): string | null {
  const match = TRACK_TOKEN.exec(text);
  return match ? (match[1] ?? match[2]) : null;
}

export function routeAiCommand(rawText: string, targets: NavTarget[]): AiCommandMatch | null {
  const text = rawText.trim();
  if (!text) return null;

  const token = extractTrackToken(text);
  const trackTarget = targets.find((t) => t.id === 'track');
  if (token && trackTarget) {
    return { route: trackTarget.route, label: `Track ${token}`, queryParams: { q: token } };
  }

  const condensedQuery = condense(text);
  const queryWords = new Set(significantWords(text));

  let best: { target: NavTarget; score: number } | null = null;
  for (const target of targets) {
    let score = 0;

    const aliasPhrases = ALIASES[target.id] ?? [];
    for (const phrase of aliasPhrases) {
      const condensedPhrase = condense(phrase);
      if (condensedQuery.includes(condensedPhrase) || condensedPhrase.includes(condensedQuery)) {
        score = Math.max(score, 10 - Math.abs(condensedPhrase.length - condensedQuery.length) * 0.01);
      }
    }

    const condensedTitle = condense(target.title);
    if (condensedQuery.includes(condensedTitle) || condensedTitle.includes(condensedQuery)) {
      score = Math.max(score, 8);
    }

    const titleWords = significantWords(target.title);
    const overlap = titleWords.filter((w) => queryWords.has(w)).length;
    if (overlap > 0) score = Math.max(score, overlap * 2);

    if (score > 0 && (!best || score > best.score)) best = { target, score };
  }

  if (!best) return null;
  return { route: best.target.route, label: best.target.title };
}
