#!/usr/bin/env python3
"""Original EL110 audio synthesis; no samples, recordings, libraries, or third-party sounds.

Rebuild from repository root: python3 tools/generate_audio.py
22,050 Hz mono PCM16; deterministic seed; 0.68 peak maximum and short anti-click ramps.
These are fictional UI/game cues and do not reproduce actual police signaling.
"""
from pathlib import Path
import math
import random
import struct
import wave

RATE = 22050
DEST = Path(__file__).resolve().parents[1] / 'app/src/main/native_assets/audio'
DEST.mkdir(parents=True, exist_ok=True)

def render(name, duration, recipe):
    rng = random.Random(110 + sum(ord(c) for c in name))
    values = []
    for frame in range(round(duration * RATE)):
        t = frame / RATE
        # All cues fade to silence to avoid discontinuities at start/end.
        envelope = min(1, t / .012, (duration - t) / .025)
        values.append(recipe(t, rng) * max(0, envelope))
    peak = max(map(abs, values)) or 1
    scale = min(.68 / peak, 1)
    with wave.open(str(DEST / (name + '.wav')), 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(b''.join(struct.pack('<h', round(max(-1, min(1, n * scale)) * 32767)) for n in values))

def sine(t, hz):
    return math.sin(2 * math.pi * hz * t)

def note(t, start, length, hz, level=.4):
    x = t - start
    if not 0 <= x < length:
        return 0
    env = min(1, x / .008, (length - x) / .025)
    return level * max(0, env) * (sine(x, hz) + .12 * sine(x, hz * 2))

render('incoming_call', .62, lambda t, r: note(t, 0, .19, 660) + note(t, .29, .23, 880))
render('p1_alarm', .88, lambda t, r: sum(note(t, s, .14, h, .36) for s, h in [(0, 1040), (.20, 760), (.40, 1040), (.60, 760)]))
render('p2_alarm', .55, lambda t, r: note(t, 0, .18, 760, .32) + note(t, .24, .22, 570, .32))
render('radio_squelch', .24, lambda t, r: r.uniform(-1, 1) * .17 * math.exp(-t * 14) + .05 * sine(t, 1650) * math.exp(-t * 22))
render('dispatch_ack', .19, lambda t, r: note(t, 0, .07, 950, .28) + note(t, .08, .09, 1280, .24))
render('unit_departure', .78, lambda t, r: .17 * math.sin(2 * math.pi * (72 * t + 92 * t * t)) * math.sin(math.pi * t / .78) + .035 * r.uniform(-1, 1) * (1 - t / .78))
render('incident_success', .66, lambda t, r: note(t, 0, .23, 523.25, .27) + note(t, .16, .25, 659.25, .27) + note(t, .33, .28, 783.99, .27))
render('incident_failure', .58, lambda t, r: note(t, 0, .21, 330, .3) + note(t, .26, .26, 220, .3))
render('shift_end', 1.36, lambda t, r: sum(note(t, s, d, h, .24) for s, d, h in [(0, .32, 392), (.23, .32, 493.88), (.46, .32, 587.33), (.74, .52, 783.99)]))
render('ambient_dispatch', 4.0, lambda t, r: .012 * r.uniform(-1, 1) + .018 * sine(t, 73) + .009 * sine(t, 146) + .012 * sine(t, 211) * (.5 + .5 * sine(t, .25)))
print('Generated 10 original mono PCM16 cues in', DEST)
