import { describe, expect, it } from 'vitest';
import { csvCell, legacyReportCsv } from './legacy-csv';

describe('legacy report CSV', () => {
  it('neutralises formula-looking cells', () => {
    expect(csvCell('=HYPERLINK("x")')).toBe(`"'=HYPERLINK(""x"")"`);
    expect(csvCell('+1')).toBe(`"'+1"`);
    expect(csvCell('-2')).toBe(`"'-2"`);
    expect(csvCell('@SUM')).toBe(`"'@SUM"`);
  });

  it('leaves ordinary cells alone and doubles quotes', () => {
    expect(csvCell('SO912C2627215')).toBe('"SO912C2627215"');
    expect(csvCell('a "b"')).toBe('"a ""b"""');
    expect(csvCell(null)).toBe('""');
  });

  it('writes a header and one line per row', () => {
    const csv = legacyReportCsv([
      { rowNo: 1, consignmentNo: 'A1', outcome: 'EXISTING', reason: null, differences: ['weight: ours 9 / file 3'], origin: 'PUNE' }
    ]);
    const lines = csv.split('\n');
    expect(lines).toHaveLength(2);
    expect(lines[0]).toContain('"Consignment No"');
    expect(lines[1]).toBe('"1","A1","PUNE","EXISTING","","weight: ours 9 / file 3"');
  });
});
