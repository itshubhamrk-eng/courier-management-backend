/** Minimal ambient type for the `jsbarcode` package — it ships no official types and
 *  this codebase uses only the single-call SVG-element form (see
 *  `consignment-print.util.ts`'s `barcodeSvg`). */
declare module 'jsbarcode' {
  interface JsBarcodeOptions {
    format?: string;
    displayValue?: boolean;
    margin?: number;
    height?: number;
    width?: number;
    background?: string;
    lineColor?: string;
  }

  function JsBarcode(
    element: SVGElement | HTMLCanvasElement | string,
    value: string,
    options?: JsBarcodeOptions
  ): void;

  export default JsBarcode;
}
