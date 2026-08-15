/**
 * Shared wrapper around the browser's Web Speech API (`SpeechRecognition` /
 * `webkitSpeechRecognition`) — Chrome/Edge only, no backend, no API key. Extracted so
 * both `VoiceMicButton` (shipment booking) and `AiAssistantButton` (dashboard command
 * bar) drive one recognition session shape instead of two copies of the same typings.
 */
export interface SpeechRecognitionAlternative { transcript: string; confidence: number; }
export interface SpeechRecognitionResult { readonly isFinal: boolean; readonly length: number; item(index: number): SpeechRecognitionAlternative; }
export interface SpeechRecognitionResultList { readonly length: number; item(index: number): SpeechRecognitionResult; }
export interface SpeechRecognitionEvent extends Event { readonly resultIndex: number; readonly results: SpeechRecognitionResultList; }
export interface SpeechRecognitionErrorEvent extends Event { readonly error: string; }
export interface SpeechRecognition extends EventTarget {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  start(): void;
  stop(): void;
  abort(): void;
  onresult: ((this: SpeechRecognition, ev: SpeechRecognitionEvent) => unknown) | null;
  onerror: ((this: SpeechRecognition, ev: SpeechRecognitionErrorEvent) => unknown) | null;
  onend: ((this: SpeechRecognition, ev: Event) => unknown) | null;
}
declare global {
  interface Window {
    SpeechRecognition?: new () => SpeechRecognition;
    webkitSpeechRecognition?: new () => SpeechRecognition;
  }
}

export function speechRecognitionSupported(): boolean {
  return typeof window !== 'undefined' && !!(window.SpeechRecognition ?? window.webkitSpeechRecognition);
}

export interface SpeechSession {
  stop(): void;
  abort(): void;
}

/** Starts one recognition session. `onInterim` fires as partial results arrive;
 *  `onFinal` fires once, only if the session actually captured non-empty speech
 *  (silence/`no-speech` ends quietly, matching how a mic that heard nothing should
 *  behave — not an error). `onEnd` always fires when the session stops, error or not,
 *  so callers can reset their "listening" state in one place. Returns `null` if this
 *  browser has no Web Speech API at all. */
export function startSpeechRecognition(lang: string, handlers: {
  onInterim: (text: string) => void;
  onFinal: (text: string) => void;
  onError: (message: string) => void;
  onEnd: () => void;
}): SpeechSession | null {
  const Ctor = typeof window === 'undefined' ? undefined : (window.SpeechRecognition ?? window.webkitSpeechRecognition);
  if (!Ctor) return null;

  const recognition = new Ctor();
  recognition.lang = lang;
  recognition.continuous = false;
  recognition.interimResults = true;
  recognition.maxAlternatives = 1;

  let finalText = '';
  recognition.onresult = (event: SpeechRecognitionEvent) => {
    let interimText = '';
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const result = event.results.item(i);
      const alt = result.item(0);
      if (result.isFinal) finalText += `${alt.transcript} `;
      else interimText += alt.transcript;
    }
    handlers.onInterim((finalText + interimText).trim());
  };
  recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
    if (event.error === 'no-speech') return;
    const message = event.error === 'not-allowed' || event.error === 'service-not-allowed'
      ? 'Microphone access was denied — allow it in the browser to use voice.'
      : `Voice recognition error: ${event.error}.`;
    handlers.onError(message);
  };
  recognition.onend = () => {
    handlers.onEnd();
    const text = finalText.trim();
    if (text) handlers.onFinal(text);
  };

  recognition.start();
  return { stop: () => recognition.stop(), abort: () => recognition.abort() };
}
