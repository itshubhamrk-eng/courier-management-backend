/**
 * Rule-based transcript → structured booking fields for voice-command shipment booking.
 * No AI/LLM call — pure regex/keyword slot extraction, chosen for zero setup cost (no
 * API key, no backend round trip) and deterministic output. Trade-off: the caller must
 * speak with light structure ("sender/receiver", "pickup/delivery pincode", "weight",
 * "packages", "payment mode", …) — very casual phrasing will under-fill, not crash.
 *
 * <p>English, Hindi and Marathi keyword sets are all checked on every transcript
 * regardless of which recognition language the mic was set to — a common Indian
 * booking-desk sentence code-switches ("Rahul ko weight paanch kilo"), so there's no
 * reliable single "this transcript is Hindi" signal to gate on. Hindi/Marathi coverage
 * is best-effort common phrasing, not exhaustive — see `voice-mic-button.ts` for the
 * recognition-language picker that controls transcription accuracy itself.
 */
export interface VoiceParsedFields {
  senderName?: string;
  senderContact?: string;
  senderAddress?: string;
  pickupPincode?: string;
  receiverName?: string;
  receiverContact?: string;
  receiverAddress?: string;
  deliveryPincode?: string;
  weightKg?: number;
  numberOfPackages?: number;
  declaredValue?: number;
  paymentModeText?: string;
  deliveryBranchText?: string;
  serviceTypeText?: string;
  packageTypeText?: string;
  remarks?: string;
}

type RawKey =
  | 'senderName' | 'senderContact' | 'senderAddress' | 'pickupPincode'
  | 'receiverName' | 'receiverContact' | 'receiverAddress' | 'deliveryPincode'
  | 'deliveryBranch' | 'serviceType' | 'packageType'
  | 'weight' | 'packages' | 'value' | 'payment' | 'remarks';

/** JS `\b` is ASCII-only — it never fires around Devanagari letters (they aren't `\w`),
 *  so a `\bसंपर्क\b` pattern would silently never match. This builds a real Unicode
 *  letter/number boundary instead, for any script. */
function wb(word: string): RegExp {
  return new RegExp(`(?<![\\p{L}\\p{N}])${word}(?![\\p{L}\\p{N}])`, 'iu');
}

/** Each slot's patterns are checked most-specific-first; only the first pattern that
 *  matches is used, and only its first occurrence in the transcript — so "sender contact"
 *  never gets mistaken for the generic "sender" (name) slot, thanks to the negative
 *  lookahead on the bare "sender"/"receiver" fallback. Hindi/Marathi alternatives are
 *  plain labelled terms only (प्रेषक, वजन, …) — no generic single-word fallback like
 *  English's "from"/"to", since those are exactly the ambiguous case that needed a
 *  lookahead guard in English and there's no reliable equivalent guard to write blind. */
const SLOTS: { key: RawKey; patterns: RegExp[] }[] = [
  { key: 'senderName', patterns: [
    /\bsender name\b/i, /\bconsignor\b/i, /\bsender\b(?!\s*(contact|phone|mobile|number|address))/i, /\bfrom\b/i,
    wb('प्रेषक का नाम'), wb('भेजने वाला'), wb('भेजने वाले का नाम'), wb('प्रेषक'),
    wb('पाठवणाऱ्याचे नाव'), wb('पाठवणारा')
  ] },
  { key: 'senderContact', patterns: [
    /\bsender contact\b/i, /\bsender (?:phone|mobile) number\b/i, /\bsender (?:phone|mobile|number)\b/i,
    wb('प्रेषक का नंबर'), wb('प्रेषक संपर्क'), wb('भेजने वाले का नंबर'),
    wb('पाठवणाऱ्याचा नंबर'), wb('पाठवणाऱ्याचा संपर्क')
  ] },
  { key: 'senderAddress', patterns: [
    /\bsender address\b/i, /\bpickup address\b/i,
    wb('प्रेषक का पता'), wb('पिकअप का पता'), wb('पिकअप पता'),
    wb('पाठवणाऱ्याचा पत्ता'), wb('पिकअप पत्ता')
  ] },
  { key: 'pickupPincode', patterns: [
    /\bpickup pin ?code\b/i, /\bpickup postal code\b/i, /\bsender pin ?code\b/i,
    wb('पिकअप पिन ?कोड'), wb('पिकअप पिन कोड')
  ] },
  { key: 'receiverName', patterns: [
    /\breceiver name\b/i, /\bconsignee\b/i, /\breceiver\b(?!\s*(contact|phone|mobile|number|address))/i, /\bto\b(?!\s*(pay|be billed))/i,
    wb('प्राप्तकर्ता का नाम'), wb('प्राप्तकर्ता'), wb('पाने वाला'), wb('पाने वाले का नाम'),
    wb('प्राप्तकर्त्याचे नाव'), wb('मिळणारा')
  ] },
  { key: 'receiverContact', patterns: [
    /\breceiver contact\b/i, /\breceiver (?:phone|mobile) number\b/i, /\breceiver (?:phone|mobile|number)\b/i,
    wb('प्राप्तकर्ता का नंबर'), wb('प्राप्तकर्ता संपर्क'), wb('पाने वाले का नंबर'),
    wb('प्राप्तकर्त्याचा नंबर'), wb('प्राप्तकर्त्याचा संपर्क')
  ] },
  { key: 'receiverAddress', patterns: [
    /\breceiver address\b/i, /\bdelivery address\b/i,
    wb('प्राप्तकर्ता का पता'), wb('डिलीवरी का पता'), wb('डिलीवरी पता'),
    wb('प्राप्तकर्त्याचा पत्ता'), wb('डिलिव्हरी पत्ता')
  ] },
  { key: 'deliveryPincode', patterns: [
    /\bdelivery pin ?code\b/i, /\bdelivery postal code\b/i, /\breceiver pin ?code\b/i,
    wb('डिलीवरी पिन ?कोड'), wb('डिलिव्हरी पिन ?कोड')
  ] },
  { key: 'deliveryBranch', patterns: [
    /\bdelivery branch\b/i, /\bbranch\b/i, wb('डिलीवरी शाखा'), wb('शाखा'), wb('ब्रांच')
  ] },
  { key: 'serviceType', patterns: [/\bservice type\b/i, wb('सेवा प्रकार'), wb('सर्विस टाइप')] },
  { key: 'packageType', patterns: [/\bpackage type\b/i, wb('पैकेज प्रकार'), wb('पॅकेज प्रकार')] },
  { key: 'weight', patterns: [/\bweight\b/i, wb('वजन')] },
  { key: 'packages', patterns: [
    /\bnumber of packages\b/i, /\bpackages\b/i, /\bpkgs\b/i,
    wb('पैकेजों की संख्या'), wb('पैकेज'), wb('पैकेट'), wb('पॅकेजेसची संख्या'), wb('पॅकेज')
  ] },
  { key: 'value', patterns: [
    /\bdeclared value\b/i, /\binsured value\b/i, /\bvalue\b/i, /\bworth\b/i,
    wb('घोषित मूल्य'), wb('कीमत'), wb('मूल्य'), wb('घोषित किंमत'), wb('किंमत')
  ] },
  { key: 'payment', patterns: [
    /\bpayment mode\b/i, /\bpay mode\b/i, /\bpayment\b/i,
    wb('भुगतान का तरीका'), wb('भुगतान'), wb('पेमेंट पद्धत'), wb('पेमेंट')
  ] },
  { key: 'remarks', patterns: [
    /\bremarks\b/i, /\bnotes?\b/i, /\binstructions?\b/i,
    wb('टिप्पणी'), wb('निर्देश'), wb('टीप'), wb('सूचना')
  ] }
];

const ONES: Record<string, number> = {
  zero: 0, one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9,
  ten: 10, eleven: 11, twelve: 12, thirteen: 13, fourteen: 14, fifteen: 15, sixteen: 16,
  seventeen: 17, eighteen: 18, nineteen: 19
};
const TENS: Record<string, number> = {
  twenty: 20, thirty: 30, forty: 40, fifty: 50, sixty: 60, seventy: 70, eighty: 80, ninety: 90
};

/** Hindi + Marathi number words merged into one lookup — spellings that coincide
 *  (एक/one, तीन/three, …) just map to the same number twice, no conflict. */
const ONES_DEV: Record<string, number> = {
  'एक': 1, 'दो': 2, 'दोन': 2, 'तीन': 3, 'चार': 4, 'पांच': 5, 'पाँच': 5, 'पाच': 5,
  'छह': 6, 'छे': 6, 'सहा': 6, 'सात': 7, 'आठ': 8, 'नौ': 9, 'नऊ': 9,
  'दस': 10, 'दहा': 10, 'ग्यारह': 11, 'अकरा': 11, 'बारह': 12, 'बारा': 12,
  'तेरह': 13, 'तेरा': 13, 'चौदह': 14, 'चौदा': 14, 'पंद्रह': 15, 'पंधरा': 15,
  'सोलह': 16, 'सोळा': 16, 'सत्रह': 17, 'सतरा': 17, 'अठारह': 18, 'अठरा': 18,
  'उन्नीस': 19, 'एकोणीस': 19
};
const TENS_DEV: Record<string, number> = {
  'बीस': 20, 'वीस': 20, 'तीस': 30, 'चालीस': 40, 'चाळीस': 40, 'पचास': 50, 'पन्नास': 50,
  'साठ': 60, 'सत्तर': 70, 'अस्सी': 80, 'ऐंशी': 80, 'नब्बे': 90, 'नव्वद': 90
};
const HUNDRED_WORDS = new Set(['hundred', 'सौ', 'शंभर']);
const THOUSAND_WORDS = new Set(['thousand', 'हज़ार', 'हजार']);
const AND_WORDS = new Set(['and', 'और', 'आणि']);

/** Devanagari uses its own digit glyphs (०-९) — Indian-language speech recognisers
 *  occasionally transcribe numbers with them instead of Latin digits. Normalised once,
 *  up front, so every digit-regex below (`extractPhone`/`extractPincode`/`wordsToNumber`)
 *  just works without knowing this happened. */
const DEVANAGARI_DIGITS: Record<string, string> = {
  '०': '0', '१': '1', '२': '2', '३': '3', '४': '4', '५': '5', '६': '6', '७': '7', '८': '8', '९': '9'
};
function normalizeDigits(s: string): string {
  return s.replace(/[०-९]/g, (d) => DEVANAGARI_DIGITS[d] ?? d);
}

/** Slices the transcript between consecutive label matches — each slot's value is
 *  "everything said after this label, up to the next recognised label". */
function extractRawSlots(transcript: string): Partial<Record<RawKey, string>> {
  const text = ` ${transcript.trim()} `;
  const hits: { key: RawKey; start: number; end: number }[] = [];
  for (const slot of SLOTS) {
    for (const pattern of slot.patterns) {
      const match = pattern.exec(text);
      if (match) {
        hits.push({ key: slot.key, start: match.index, end: match.index + match[0].length });
        break;
      }
    }
  }
  hits.sort((a, b) => a.start - b.start);

  const result: Partial<Record<RawKey, string>> = {};
  for (let i = 0; i < hits.length; i++) {
    const nextStart = i + 1 < hits.length ? hits[i + 1].start : text.length;
    const value = text.slice(hits[i].end, nextStart).replace(/[,.]+$/, '').trim();
    if (value) result[hits[i].key] = value;
  }
  return result;
}

function cleanText(s: string): string {
  return s.replace(/^(is|:|-|है|:)\s*/i, '').trim();
}

function extractPhone(s: string): string | null {
  const match = s.replace(/\D+/g, '').match(/\d{10}/);
  return match ? match[0] : null;
}

function extractPincode(s: string): string | null {
  const match = s.replace(/\D+/g, '').match(/\d{6}/);
  return match ? match[0] : null;
}

/** "five" / "पांच" / "पाच" → 5, "two thousand five hundred" → 2500, "5" → 5. Digits
 *  (Latin or Devanagari) win outright if present. */
function wordsToNumber(text: string): number | null {
  const normalized = normalizeDigits(text);
  const digitMatch = normalized.match(/\d+(\.\d+)?/);
  if (digitMatch) return parseFloat(digitMatch[0]);

  const words = normalized.replace(/-/g, ' ').split(/\s+/).filter(Boolean);
  let total = 0;
  let current = 0;
  let matched = false;
  for (const raw of words) {
    const word = raw.toLowerCase();
    if (AND_WORDS.has(word)) continue;
    if (word in ONES) { current += ONES[word]; matched = true; }
    else if (word in TENS) { current += TENS[word]; matched = true; }
    else if (word in ONES_DEV) { current += ONES_DEV[word]; matched = true; }
    else if (word in TENS_DEV) { current += TENS_DEV[word]; matched = true; }
    else if (HUNDRED_WORDS.has(word)) { current = (current || 1) * 100; matched = true; }
    else if (THOUSAND_WORDS.has(word)) { total += (current || 1) * 1000; current = 0; matched = true; }
  }
  return matched ? total + current : null;
}

function detectPaymentMode(text: string): string | null {
  const t = text.toLowerCase();
  if (/\bcash on delivery\b|\bcod\b/.test(t)
    || wb('नकद वितरण पर').test(t) || wb('कैश ऑन डिलीवरी').test(t) || wb('सीओडी').test(t)
    || wb('रोख रक्कम').test(t) || wb('कॅश ऑन डिलिव्हरी').test(t)) return 'cod';
  if (/\bto pay\b/.test(t) || wb('डिलीवरी पर भुगतान').test(t) || wb('डिलिव्हरीच्या वेळी पैसे').test(t)) return 'to pay';
  if (/\bto be billed\b|\btbb\b/.test(t)) return 'tbb';
  if (/\bprepaid\b|\bpaid\b/.test(t) || wb('प्रीपेड').test(t) || wb('पेड').test(t)) return 'paid';
  return null;
}

export function parseVoiceBooking(transcript: string): VoiceParsedFields {
  const raw = extractRawSlots(transcript);
  const fields: VoiceParsedFields = {};

  if (raw.senderName) fields.senderName = cleanText(raw.senderName);
  if (raw.senderContact) { const v = extractPhone(raw.senderContact); if (v) fields.senderContact = v; }
  if (raw.senderAddress) fields.senderAddress = cleanText(raw.senderAddress);
  if (raw.pickupPincode) { const v = extractPincode(raw.pickupPincode); if (v) fields.pickupPincode = v; }
  if (raw.receiverName) fields.receiverName = cleanText(raw.receiverName);
  if (raw.receiverContact) { const v = extractPhone(raw.receiverContact); if (v) fields.receiverContact = v; }
  if (raw.receiverAddress) fields.receiverAddress = cleanText(raw.receiverAddress);
  if (raw.deliveryPincode) { const v = extractPincode(raw.deliveryPincode); if (v) fields.deliveryPincode = v; }
  if (raw.deliveryBranch) fields.deliveryBranchText = cleanText(raw.deliveryBranch);
  if (raw.serviceType) fields.serviceTypeText = cleanText(raw.serviceType);
  if (raw.packageType) fields.packageTypeText = cleanText(raw.packageType);
  if (raw.weight) { const v = wordsToNumber(raw.weight); if (v != null) fields.weightKg = v; }
  if (raw.packages) { const v = wordsToNumber(raw.packages); if (v != null) fields.numberOfPackages = Math.round(v); }
  if (raw.value) { const v = wordsToNumber(raw.value); if (v != null) fields.declaredValue = v; }
  if (raw.remarks) fields.remarks = cleanText(raw.remarks);

  const paymentMode = detectPaymentMode(raw.payment ?? '') ?? detectPaymentMode(transcript);
  if (paymentMode) fields.paymentModeText = paymentMode;

  return fields;
}
