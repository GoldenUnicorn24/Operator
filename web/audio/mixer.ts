/** Offline WebAudio mixer. Original WAV sources are reproducible with tools/generate_audio.py. */
export interface MixerSettings {
  master: number;
  radio: number;
  sfx: number;
  ambient: number;
  mute: boolean;
}
export type AudioChannel = "radio" | "sfx" | "ambient";
export const CUES: Record<string, { channel: AudioChannel; file: string }> = {
  incoming_call: { channel: "sfx", file: "incoming_call.wav" },
  p1_alarm: { channel: "sfx", file: "p1_alarm.wav" },
  p2_alarm: { channel: "sfx", file: "p2_alarm.wav" },
  radio_squelch: { channel: "radio", file: "radio_squelch.wav" },
  dispatch_ack: { channel: "radio", file: "dispatch_ack.wav" },
  unit_departure: { channel: "sfx", file: "unit_departure.wav" },
  incident_success: { channel: "sfx", file: "incident_success.wav" },
  incident_failure: { channel: "sfx", file: "incident_failure.wav" },
  shift_end: { channel: "sfx", file: "shift_end.wav" },
  ambient_dispatch: { channel: "ambient", file: "ambient_dispatch.wav" },
};
const DEFAULTS: MixerSettings = {
  master: 0.65,
  radio: 0.7,
  sfx: 0.65,
  ambient: 0.16,
  mute: false,
};
export class AudioMixer {
  private settings: MixerSettings;
  private context: AudioContext | null = null;
  private channels = new Map<AudioChannel, GainNode>();
  private buffers = new Map<string, Promise<AudioBuffer | null>>();
  private sources = new Set<AudioBufferSourceNode>();
  private lastPlayed = new Map<string, number>();
  private suspended = false;
  private generation = 0;
  /** Cue requests remain accessible when muted: the UI can show the same events as text. */
  readonly cueLog: { cue: string; at: number }[] = [];
  constructor(settings: Partial<MixerSettings> = {}) {
    this.settings = { ...DEFAULTS };
    for (const key of Object.keys(DEFAULTS) as (keyof MixerSettings)[]) {
      if (settings[key] !== undefined) this.set(key, settings[key]!);
    }
  }
  getSettings(): MixerSettings {
    return { ...this.settings };
  }
  set(channel: keyof MixerSettings, value: number | boolean): void {
    if (channel === "mute") this.settings.mute = Boolean(value);
    else if (
      channel in DEFAULTS &&
      typeof value === "number" &&
      Number.isFinite(value)
    )
      this.settings[channel] = Math.max(0, Math.min(1, value));
    this.updateGains();
    if (this.settings.mute || this.settings.master === 0) this.stopSources();
  }
  private updateGains(): void {
    for (const [channel, gain] of this.channels)
      gain.gain.value = this.settings.mute
        ? 0
        : this.settings.master * this.settings[channel];
  }
  private init(): AudioContext | null {
    if (this.context) return this.context;
    // No audio context is created before the first resume/play request from a UI gesture.
    const Constructor =
      typeof globalThis !== "undefined" ? globalThis.AudioContext : undefined;
    if (!Constructor) return null;
    try {
      this.context = new Constructor();
      for (const channel of ["radio", "sfx", "ambient"] as AudioChannel[]) {
        const gain = this.context.createGain();
        gain.connect(this.context.destination);
        this.channels.set(channel, gain);
      }
      this.updateGains();
      return this.context;
    } catch {
      return null;
    }
  }
  private async load(
    cue: string,
    ctx: AudioContext,
  ): Promise<AudioBuffer | null> {
    if (!this.buffers.has(cue)) {
      this.buffers.set(
        cue,
        (async () => {
          try {
            const response = await fetch(`audio/${CUES[cue].file}`);
            if (!response.ok) return null;
            return await ctx.decodeAudioData(await response.arrayBuffer());
          } catch {
            return null;
          }
        })(),
      );
    }
    return this.buffers.get(cue)!;
  }
  play(cue: string): void {
    if (!CUES[cue]) return;
    const now = Date.now();
    this.cueLog.push({ cue, at: now });
    if (this.cueLog.length > 100) this.cueLog.shift();
    if (
      typeof globalThis.dispatchEvent === "function" &&
      typeof CustomEvent !== "undefined"
    ) {
      globalThis.dispatchEvent(
        new CustomEvent("el110:audio-cue", { detail: { cue } }),
      );
    }
    const { channel } = CUES[cue];
    if (
      this.suspended ||
      this.settings.mute ||
      this.settings.master === 0 ||
      this.settings[channel] === 0
    )
      return;
    // Per-cue cooldown avoids repeated incident rendering producing audio bursts.
    if (now - (this.lastPlayed.get(cue) ?? -Infinity) < 100) return;
    this.lastPlayed.set(cue, now);
    const ctx = this.init();
    if (!ctx || ctx.state !== "running") return;
    const generation = this.generation;
    void this.load(cue, ctx).then((buffer) => {
      if (
        !buffer ||
        generation !== this.generation ||
        this.suspended ||
        this.settings.mute ||
        ctx.state !== "running"
      )
        return;
      while (this.sources.size >= 6) {
        const oldest = this.sources.values().next().value;
        if (!oldest) break;
        this.sources.delete(oldest);
        try {
          oldest.stop();
        } catch {
          /* already finished */
        }
      }
      const source = ctx.createBufferSource();
      source.buffer = buffer;
      source.connect(this.channels.get(channel)!);
      this.sources.add(source);
      source.onended = () => {
        this.sources.delete(source);
        source.disconnect();
      };
      source.start();
    });
  }
  private stopSources(): void {
    this.generation++;
    for (const source of this.sources) {
      try {
        source.stop();
      } catch {
        /* already ended */
      }
    }
    this.sources.clear();
  }
  suspend(): void {
    this.suspended = true;
    this.stopSources();
    if (this.context && this.context.state !== "closed")
      void this.context.suspend().catch(() => {});
  }
  /** Call from a user gesture; also used after Android AudioFocus returns. */
  resume(): void {
    this.suspended = false;
    const ctx = this.init();
    if (ctx && ctx.state !== "closed") void ctx.resume().catch(() => {});
  }
  destroy(): void {
    this.suspend();
    if (this.context && this.context.state !== "closed")
      void this.context.close().catch(() => {});
    this.buffers.clear();
  }
}
