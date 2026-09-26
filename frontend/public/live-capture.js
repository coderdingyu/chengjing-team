/* Browser-only PCM capture for a consented Live session. No recording is persisted. */
class ChengjingLiveCapture extends AudioWorkletProcessor {
  constructor() { super(); this.pending = []; this.length = 0; }
  process(inputs) {
    const source = inputs[0]?.[0];
    if (!source) return true;
    this.pending.push(new Float32Array(source)); this.length += source.length;
    if (this.length < 1200) return true;
    const pcm = new Int16Array(this.length);
    let offset = 0;
    for (const chunk of this.pending) {
      for (let i = 0; i < chunk.length; i++) {
        const value = Math.max(-1, Math.min(1, chunk[i]));
        pcm[offset++] = value < 0 ? value * 32768 : value * 32767;
      }
    }
    this.port.postMessage(pcm.buffer, [pcm.buffer]);
    this.pending = []; this.length = 0;
    return true;
  }
}
registerProcessor("chengjing-live-capture", ChengjingLiveCapture);
