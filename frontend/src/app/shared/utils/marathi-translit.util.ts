/**
 * Rough Latin -> Devanagari (Marathi) transliteration for place names. Phonetic and
 * rule-based, so it is approximate: area masters hold English names only, and this is
 * shown beside them as a reading aid, never stored.
 */
const WORDS: Record<string, string> = {
  pune: 'पुणे', mumbai: 'मुंबई', nashik: 'नाशिक', thane: 'ठाणे', nagpur: 'नागपूर', satara: 'सातारा',
  kolhapur: 'कोल्हापूर', sangli: 'सांगली', solapur: 'सोलापूर', peth: 'पेठ', nagar: 'नगर', road: 'रोड',
  colony: 'कॉलनी', chowk: 'चौक', gaon: 'गाव', wadi: 'वाडी', pur: 'पूर', and: 'आणि', city: 'सिटी',
  camp: 'कॅम्प', market: 'मार्केट', station: 'स्टेशन', society: 'सोसायटी', park: 'पार्क', gaothan: 'गावठाण'
};

const CONSONANTS: [string, string][] = [
  ['ksh', 'क्ष'], ['dny', 'ज्ञ'], ['chh', 'छ'], ['kh', 'ख'], ['gh', 'घ'], ['ch', 'च'], ['jh', 'झ'],
  ['th', 'थ'], ['dh', 'ध'], ['ph', 'फ'], ['bh', 'भ'], ['sh', 'श'],
  ['k', 'क'], ['g', 'ग'], ['c', 'क'], ['j', 'ज'], ['t', 'त'], ['d', 'द'], ['n', 'न'], ['p', 'प'],
  ['b', 'ब'], ['m', 'म'], ['y', 'य'], ['r', 'र'], ['l', 'ल'], ['v', 'व'], ['w', 'व'], ['s', 'स'],
  ['h', 'ह'], ['f', 'फ'], ['z', 'ज'], ['q', 'क'], ['x', 'क्स']
];
const VOWELS: [string, string, string][] = [   // [roman, independent, matra]
  ['aa', 'आ', 'ा'], ['ai', 'ऐ', 'ै'], ['au', 'औ', 'ौ'], ['ee', 'ई', 'ी'], ['ii', 'ई', 'ी'],
  ['oo', 'ऊ', 'ू'], ['uu', 'ऊ', 'ू'], ['a', 'अ', ''], ['i', 'इ', 'ि'], ['u', 'उ', 'ु'],
  ['e', 'ए', 'े'], ['o', 'ओ', 'ो']
];
const HALANT = '्';
const NASAL_OK = new Set(['k', 'g', 'c', 'j', 't', 'd', 'p', 'b', 's', 'h', 'y', 'r', 'l', 'v']);

function word(w: string): string {
  const key = w.toLowerCase();
  if (WORDS[key]) return WORDS[key];
  let out = '';
  let pending: { dev: string; roman: string } | null = null;
  const flush = () => { if (pending) { out += pending.dev; pending = null; } };
  let i = 0;
  while (i < key.length) {
    const rest = key.slice(i);
    const v = VOWELS.find(([r]) => rest.startsWith(r));
    if (v) {
      let [r, indep, matra] = v;
      const atEnd = i + r.length >= key.length;
      if (atEnd && r === 'a' && pending && key.length > 3) matra = 'ा';
      if (atEnd && r === 'i') matra = 'ी';
      if (atEnd && r === 'u') matra = 'ू';
      if (pending) { out += pending.dev + matra; pending = null; } else { out += indep; }
      i += r.length;
      continue;
    }
    const c = CONSONANTS.find(([r]) => rest.startsWith(r));
    if (c) {
      if (pending) {
        const p: { dev: string; roman: string } = pending;
        const nasal = (p.roman === 'm' || p.roman === 'n') && NASAL_OK.has(c[0][0]);
        out += nasal ? p.dev.replace(/[नम]$/, '') + 'ं' : p.dev + HALANT;
      }
      pending = { dev: c[1], roman: c[0] };
      i += c[0].length;
      continue;
    }
    flush();
    out += key[i];
    i++;
  }
  flush();
  return out;
}

/** Devanagari reading of an English place name; digits and punctuation pass through. */
export function toMarathi(text: string | null | undefined): string {
  if (!text) return '';
  return text.replace(/[A-Za-z]+/g, (w) => word(w));
}
