import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { AudioMixer, CUES } from "../web/audio/mixer";

// The device boundary is replaced; settings, cue delivery, loading, and lifecycle are real mixer code.
test("audio_muted: cues stay readable while mute prevents device creation or playback", () => {
  const mixer = new AudioMixer({ mute: true });
  mixer.play("p1_alarm");
  assert.equal(mixer.cueLog[0].cue, "p1_alarm");
  assert.equal(mixer.getSettings().mute, true);
  assert.equal(mixer.cueLog.length, 1);
  mixer.set("master", -1);
  assert.equal(mixer.getSettings().master, 0);
  mixer.set("sfx", 8);
  assert.equal(mixer.getSettings().sfx, 1);
  mixer.set("radio", Number.NaN);
  assert.equal(mixer.getSettings().radio, 0.7);
  const settings = mixer.getSettings();
  settings.mute = false;
  assert.equal(
    mixer.getSettings().mute,
    true,
    "returned settings cannot change active mixer",
  );
});

test("original audio assets are unique, short, mono PCM16 files", () => {
  assert.ok(Object.keys(CUES).length >= 8);
  const audioHashes = new Set<string>();
  for (const cue of Object.values(CUES)) {
    const bytes = readFileSync("app/src/main/assets/audio/" + cue.file);
    assert.equal(bytes.toString("ascii", 0, 4), "RIFF");
    assert.equal(bytes.toString("ascii", 8, 12), "WAVE");
    assert.equal(bytes.readUInt16LE(20), 1, "uncompressed PCM");
    assert.equal(bytes.readUInt16LE(22), 1, "mono");
    assert.equal(bytes.readUInt32LE(24), 22050);
    assert.equal(bytes.readUInt16LE(34), 16);
    assert.ok(bytes.length < 190000);
    let max = 0;
    for (let n = 44; n + 1 < bytes.length; n += 2)
      max = Math.max(max, Math.abs(bytes.readInt16LE(n)));
    assert.ok(max > 300 && max < 23000, "audible but no clipping");
    audioHashes.add(bytes.toString("base64"));
  }
  assert.equal(audioHashes.size, Object.keys(CUES).length);
});

test("audio focus suspension stops active sources and cancels pending cue completion", async (t) => {
  const originalContext = globalThis.AudioContext;
  const originalFetch = globalThis.fetch;
  let starts = 0,
    stops = 0;
  let completeDecode: ((b: AudioBuffer) => void) | undefined;
  class Device {
    state = "running";
    destination = {};
    createGain() {
      return { gain: { value: 0 }, connect() {} };
    }
    createBufferSource() {
      return {
        buffer: null,
        onended: null,
        connect() {},
        disconnect() {},
        start() {
          starts++;
        },
        stop() {
          stops++;
        },
      };
    }
    decodeAudioData() {
      return new Promise<AudioBuffer>((resolve) => {
        completeDecode = resolve;
      });
    }
    async suspend() {
      this.state = "suspended";
    }
    async resume() {
      this.state = "running";
    }
    async close() {
      this.state = "closed";
    }
  }
  globalThis.AudioContext = Device as unknown as typeof AudioContext;
  globalThis.fetch = (async () => ({
    ok: true,
    arrayBuffer: async () => new ArrayBuffer(1),
  })) as unknown as typeof fetch;
  t.after(() => {
    globalThis.AudioContext = originalContext;
    globalThis.fetch = originalFetch;
  });
  const mixer = new AudioMixer();
  mixer.resume();
  mixer.play("shift_end");
  await new Promise((resolve) => setImmediate(resolve));
  assert.ok(completeDecode);
  mixer.suspend();
  completeDecode!({} as AudioBuffer);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(
    starts,
    0,
    "a cue whose decode finished in background must not play",
  );
  mixer.resume();
  mixer.play("incoming_call");
  await new Promise((resolve) => setImmediate(resolve));
  completeDecode!({} as AudioBuffer);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(starts, 1);
  mixer.suspend();
  assert.equal(stops, 1, "focus loss stops a playing source immediately");
  mixer.play("incident_success");
  assert.equal(starts, 1);
  assert.equal(mixer.cueLog.at(-1)?.cue, "incident_success");
  mixer.destroy();
});
