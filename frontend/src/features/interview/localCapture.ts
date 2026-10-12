/**
 * Owner: member C.
 *
 * Registry of local capture streams (microphone / camera). It exists so a single place can stop every
 * track the page holds, which is what C04 needs when the interview is paused or ended, and what C06
 * needs when the preview is closed.
 *
 * Nothing here uploads anything: the only operations are stopping tracks and forgetting the stream.
 */

const streams = new Set<MediaStream>();

export function registerStream(stream: MediaStream): void {
  streams.add(stream);
}

export function releaseStream(stream: MediaStream): void {
  streams.delete(stream);
}

/** Stops every track of every registered stream. Safe to call when nothing is capturing. */
export function stopAllCapture(): void {
  streams.forEach((stream) => stream.getTracks().forEach((track) => track.stop()));
  streams.clear();
}

export function hasActiveCapture(): boolean {
  return streams.size > 0;
}
