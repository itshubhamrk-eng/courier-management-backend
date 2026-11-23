/** TBB (To Be Billed) is the credit-customer payment mode. Its amounts are hidden everywhere
 *  (booking summary, receipts, shipment views, reports) except the business billing invoice. */
export const isTbbLabel = (label: string | null | undefined): boolean => {
  const l = (label ?? '').toUpperCase();
  return l.endsWith('(TBB)') || l === 'TBB' || l.includes('TO BE BILLED');
};
