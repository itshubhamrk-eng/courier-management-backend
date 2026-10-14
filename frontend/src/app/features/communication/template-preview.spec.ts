import { describe, expect, it } from 'vitest';
import {
  fillVariables, insertAt, parsePlain, parseWhatsApp, sampleValues, usedVariables, variablesFor, whatsappChecks,
  wrapSelection
} from './template-preview';

describe('template preview helpers', () => {
  it('fills known variables and leaves unknown ones verbatim', () => {
    const out = fillVariables('Hi {{customerName}}, {{ trackingNumber }} {{nope}}', sampleValues());
    expect(out).toBe('Hi Rahul Sharma, AWB123456789 {{nope}}');
  });

  it('uses the real company name when given, a sample otherwise', () => {
    expect(sampleValues('Skra Tech')['companyName']).toBe('Skra Tech');
    expect(sampleValues(null)['companyName']).toBe('Amazing Logistics');
    expect(sampleValues('  ')['companyName']).toBe('Amazing Logistics');
  });

  it('does not treat a prototype key as a variable', () => {
    expect(fillVariables('{{constructor}} {{toString}}', sampleValues())).toBe('{{constructor}} {{toString}}');
  });

  it('parses WhatsApp bold, italic, strike, mono and links into segments', () => {
    const segs = parseWhatsApp('A *bold* and _italic_ and ~gone~ then ```code``` see https://x.io/a_b_c ok');
    expect(segs.filter((s) => s.bold).map((s) => s.text)).toEqual(['bold']);
    expect(segs.filter((s) => s.italic).map((s) => s.text)).toEqual(['italic']);
    expect(segs.filter((s) => s.strike).map((s) => s.text)).toEqual(['gone']);
    expect(segs.filter((s) => s.mono).map((s) => s.text)).toEqual(['code']);
    expect(segs.filter((s) => s.link).map((s) => s.text)).toEqual(['https://x.io/a_b_c']);
    expect(segs.map((s) => s.text).join('')).toBe('A bold and italic and gone then code see https://x.io/a_b_c ok');
  });

  it('does not italicise underscores inside words such as daily_business_summary', () => {
    const segs = parseWhatsApp('template daily_business_summary here');
    expect(segs).toEqual([{ text: 'template daily_business_summary here' }]);
  });

  it('keeps newlines and never produces markup', () => {
    const segs = parseWhatsApp('<b>x</b>\nline *two*');
    expect(segs[0].text).toBe('<b>x</b>\nline ');
    expect(segs[1]).toEqual({ text: 'two', bold: true });
  });

  it('handles empty and null input', () => {
    expect(parseWhatsApp('')).toEqual([]);
    expect(parseWhatsApp(null as unknown as string)).toEqual([]);
    expect(parsePlain('')).toEqual([]);
  });

  it('plain parsing only recognises links', () => {
    expect(parsePlain('see https://a.b/c *not bold*')).toEqual([
      { text: 'see ' }, { text: 'https://a.b/c', link: true }, { text: ' *not bold*' }
    ]);
  });

  it('offers the daily variables for the daily event and shipment ones otherwise', () => {
    expect(variablesFor('DAILY_BUSINESS_SUMMARY')).toContain('reportDate');
    expect(variablesFor('SHIPMENT_BOOKED')).toContain('trackingUrl');
    expect(variablesFor('SHIPMENT_BOOKED')).not.toContain('reportDate');
  });

  describe('formatting helpers', () => {
    it('wraps a selection and keeps it selected', () => {
      const r = wrapSelection('hello world', 6, 11, 'bold');
      expect(r.text).toBe('hello *world*');
      expect(r.text.slice(r.start, r.end)).toBe('world');
    });

    it('inserts an empty pair with the caret between when nothing is selected', () => {
      const r = wrapSelection('ab', 1, 1, 'italic');
      expect(r.text).toBe('a__b');
      expect(r.start).toBe(2);
      expect(r.end).toBe(2);
    });

    it('toggles off when the selection includes the marks', () => {
      const r = wrapSelection('x *bold* y', 2, 8, 'bold');
      expect(r.text).toBe('x bold y');
      expect(r.text.slice(r.start, r.end)).toBe('bold');
    });

    it('toggles off when the marks sit just outside the selection', () => {
      const r = wrapSelection('x ~gone~ y', 3, 7, 'strike');
      expect(r.text).toBe('x gone y');
      expect(r.text.slice(r.start, r.end)).toBe('gone');
    });

    it('uses triple backticks for monospace', () => {
      expect(wrapSelection('code', 0, 4, 'mono').text).toBe('```code```');
    });

    it('inserts text (an emoji) at the selection and returns the caret after it', () => {
      const r = insertAt('Shipped  now', 8, 8, '📦');
      expect(r.text).toBe('Shipped 📦 now');
      expect(r.caret).toBe(8 + '📦'.length);
      expect(insertAt('abc', 1, 2, 'X').text).toBe('aXc');
    });
  });

  describe('usedVariables', () => {
    it('lists distinct variables in order across all texts', () => {
      expect(usedVariables('A {{b}} {{a}} {{b}}', null, 'x {{c}}')).toEqual(['b', 'a', 'c']);
      expect(usedVariables(undefined, '')).toEqual([]);
    });
  });

  describe('whatsappChecks', () => {
    const base = {
      name: 'shipment_booked', body: 'Hi {{customerName}}, thanks!', header: '', footer: '', buttons: [],
      known: Object.keys(sampleValues())
    };
    const failing = (c: ReturnType<typeof whatsappChecks>) => c.filter((x) => !x.ok).map((x) => x.text);

    it('passes a clean template', () => {
      expect(failing(whatsappChecks(base))).toEqual([]);
    });

    it('flags a bad name, a body that starts or ends with a variable, and unknown variables', () => {
      expect(failing(whatsappChecks({ ...base, name: 'Bad Name' }))).toHaveLength(1);
      expect(failing(whatsappChecks({ ...base, body: '{{customerName}} hi' }))[0]).toContain('start or end');
      expect(failing(whatsappChecks({ ...base, body: 'Hi {{nope}} bye' }))[0]).toContain('{{nope}}');
    });

    it('flags header, footer and button problems', () => {
      expect(failing(whatsappChecks({ ...base, header: '{{customerName}} {{trackingNumber}}' }))[0]).toContain('Header');
      expect(failing(whatsappChecks({ ...base, footer: 'Hi {{customerName}}' }))[0]).toContain('Footer');
      const bad = (b: object) => failing(whatsappChecks({ ...base, buttons: [b as never] }))[0];
      expect(bad({ type: 'URL', text: 'Go', url: 'http://x.in' })).toContain('Button 1');
      expect(bad({ type: 'URL', text: 'Go', url: 'https://x.in/{{trackingNumber}}/more' })).toContain('Button 1');
      expect(bad({ type: 'PHONE_NUMBER', text: 'Call', phoneNumber: '9123456780' })).toContain('Button 1');
      expect(bad({ type: 'URL', text: '', url: 'https://x.in' })).toContain('Button 1');
    });

    it('accepts good buttons', () => {
      expect(failing(whatsappChecks({ ...base, buttons: [
        { type: 'URL', text: 'Track', url: 'https://x.in/t/{{trackingNumber}}' },
        { type: 'PHONE_NUMBER', text: 'Call us', phoneNumber: '+91 91234-56780' }
      ] }))).toEqual([]);
    });
  });
});
