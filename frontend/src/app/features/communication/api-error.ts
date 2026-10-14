/**
 * The app's global error handler deliberately says nothing for 400 / 422 responses — those are
 * business refusals ("the link must start with https://", "Meta rejected the access token") that the
 * screen is expected to show itself. This pulls the server's own sentence out of the error, so a
 * refusal is never swallowed silently.
 */
export function apiMessage(error: unknown, fallback: string): string {
  const message = (error as { error?: { message?: unknown } } | null)?.error?.message;
  return typeof message === 'string' && message.trim() ? message : fallback;
}
