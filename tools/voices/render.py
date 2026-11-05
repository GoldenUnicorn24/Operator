#!/usr/bin/env python3
"""Renders tools/voices/lines.tsv into app/src/main/native_assets/voices/<id>.ogg.

Neural Piper voices, used only if their model card says CC0. Runs in CI, the app stays offline.
Existing clips are kept, so a text change only renders what is new.
"""
import concurrent.futures, json, pathlib, subprocess, sys, tempfile, urllib.request, wave
from piper.voice import PiperVoice

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'app/src/main/native_assets/voices'
MODELS = ROOT / '.voice-models'
BASE = 'https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/de/de_DE'
FILES = {'thorsten': 'thorsten/high/de_DE-thorsten-high', 'thorsten_medium': 'thorsten/medium/de_DE-thorsten-medium',
         'kerstin': 'kerstin/low/de_DE-kerstin-low', 'ramona': 'ramona/low/de_DE-ramona-low', 'eva_k': 'eva_k/x_low/de_DE-eva_k-x_low'}
FEMALE_ORDER = ['kerstin', 'ramona', 'eva_k']
PHONE = 'highpass=f=220,lowpass=f=3700,acompressor=threshold=-20dB:ratio=3,alimiter=limit=0.9'
RADIO = 'highpass=f=340,lowpass=f=2800,acompressor=threshold=-24dB:ratio=6,volume=3dB,alimiter=limit=0.85'


def fetch(url, path):
    if not path.exists():
        urllib.request.urlretrieve(url, path)


def load(name):
    stem = FILES[name]
    folder = stem.rsplit('/', 1)[0]
    card = MODELS / f'{name}.card'
    fetch(f'{BASE}/{folder}/MODEL_CARD', card)
    if 'cc0' not in card.read_text().lower():
        print('skip, not CC0:', name)
        return None
    model = MODELS / f'{name}.onnx'
    fetch(f'{BASE}/{stem}.onnx', model)
    fetch(f'{BASE}/{stem}.onnx.json', MODELS / f'{name}.onnx.json')
    return PiperVoice.load(str(model))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(exist_ok=True)
    lines = [l.rstrip('\n').split('\t', 2) for l in open(ROOT / 'tools/voices/lines.tsv', encoding='utf-8')]
    wanted = {l[0] for l in lines}
    for f in OUT.glob('*.ogg'):
        if f.stem not in wanted:
            f.unlink()
    loaded = {n: load(n) for n in FILES}
    women = [loaded[n] for n in FEMALE_ORDER if loaded[n]] or [loaded['thorsten']]
    men = [loaded['thorsten'], loaded['thorsten_medium'] or loaded['thorsten']]
    voices = {'d': women[0], 'f1': women[0], 'f2': women[1 % len(women)], 'f3': women[2 % len(women)], 'm1': men[0], 'm2': men[1],
              'r0': women[1 % len(women)], 'r1': men[0], 'r2': men[1], 'r3': women[2 % len(women)]}
    speed = {'p': .84, 'a': .92, 'w': 1.05, 'n': 1.0}
    todo = [l for l in lines if not (OUT / f'{l[0]}.ogg').exists()]
    print(len(lines), 'lines,', len(todo), 'to render', flush=True)
    tmp = pathlib.Path(tempfile.mkdtemp())
    jobs = []
    for i, (cid, voice, text) in enumerate(todo):
        wav = tmp / f'{cid}.wav'
        with wave.open(str(wav), 'wb') as w:
            voices[voice[:2]].synthesize(text, w, length_scale=speed.get(voice[2:], 1.0) * (.93 if voice[0] == 'r' else 1.0))
        jobs.append((wav, voice))
        if i % 200 == 0:
            print(i, flush=True)

    def encode(job):
        wav, voice = job
        chain = RADIO if voice[0] == 'r' else 'volume=-7dB,' + PHONE if voice.endswith('w') else PHONE
        subprocess.run(['ffmpeg', '-nostdin', '-loglevel', 'error', '-y', '-i', str(wav), '-af', chain, '-ac', '1', '-ar', '24000',
                        '-c:a', 'libopus', '-b:a', '20k', '-vbr', 'on', str(OUT / f'{wav.stem}.ogg')], check=True)

    with concurrent.futures.ThreadPoolExecutor(4) as pool:
        list(pool.map(encode, jobs))
    used = {n: bool(v) for n, v in loaded.items()}
    (OUT / 'SOURCES.json').write_text(json.dumps({'engine': 'Piper', 'voices': used, 'license': 'CC0 voice models'}, indent=1))
    print('bytes', sum(f.stat().st_size for f in OUT.glob('*.ogg')))


if __name__ == '__main__':
    sys.exit(main())
