import { describe, expect, it } from 'vitest';
import { apiMessage } from './api-error';

describe('apiMessage', () => {
  it('uses the sentence the server sent', () => {
    expect(apiMessage({ error: { message: 'Button 1: a link must start with https://' } }, 'x'))
      .toBe('Button 1: a link must start with https://');
  });

  it('falls back when there is no usable message', () => {
    expect(apiMessage(null, 'Could not save.')).toBe('Could not save.');
    expect(apiMessage({}, 'Could not save.')).toBe('Could not save.');
    expect(apiMessage({ error: {} }, 'Could not save.')).toBe('Could not save.');
    expect(apiMessage({ error: { message: '   ' } }, 'Could not save.')).toBe('Could not save.');
    expect(apiMessage({ error: { message: 42 } }, 'Could not save.')).toBe('Could not save.');
    expect(apiMessage('boom', 'Could not save.')).toBe('Could not save.');
  });
});
