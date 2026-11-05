#!/usr/bin/env python3
"""AriseOS boot animation: procedural soundtrack, timed to ariseos-boot.html's EVENTS.

  trace   a soft rising shimmer while the outline draws
  fill    a gentle upward glide
  pop     a bright pluck when the inner triangle lands
  launch  an upward whoosh as the chevron flies out of the apex
  land    a deep hit and an open D chord with a long shimmering tail

Usage: python3 ariseos_sound.py [out.wav]
"""
import sys, wave
import numpy as np
from scipy import signal

SR = 48000
DUR = 5.6
EV = dict(trace=.35, fill=1.2, pop=1.85, launch=2.2, land=2.78, word=2.95, out=DUR - .75)
OUT = sys.argv[1] if len(sys.argv) > 1 else 'ariseos-boot-sound.wav'
N = int(DUR * SR) + SR // 30
rng = np.random.default_rng(20261105)
L = np.zeros(N); R = np.zeros(N)

def hz(n): return 440.0 * 2 ** ((n - 69) / 12)
def tt(d): return np.arange(int(d * SR)) / SR
def bp(x, lo, hi, o=2): return signal.sosfilt(signal.butter(o, [lo, min(hi, SR * .45)], 'bandpass', fs=SR, output='sos'), x)
def lp(x, f, o=2): return signal.sosfilt(signal.butter(o, f, 'lowpass', fs=SR, output='sos'), x)
def edge(x, a=.002, r=.01):
    na, nr = max(1, int(a * SR)), max(1, int(r * SR))
    x[:na] *= np.linspace(0, 1, na); x[-nr:] *= np.linspace(1, 0, nr); return x
def put(x, at, gain=1.0, pan=0.0):
    i = int(at * SR); x = x[:max(0, N - i)]
    L[i:i + len(x)] += x * gain * np.sqrt(.5 * (1 - pan)); R[i:i + len(x)] += x * gain * np.sqrt(.5 * (1 + pan))
def bell(f, d, dur):
    t = tt(dur)
    y = sum(a * np.sin(2 * np.pi * f * m * t) * np.exp(-t / (d * k)) for m, a, k in ((1, 1, 1), (2.01, .35, .6), (3.0, .15, .4), (4.2, .08, .25)))
    return edge(y)

# Drone: D2 + A2, slowly opening.
t = tt(DUR)
drone = (np.sin(2 * np.pi * hz(38) * t) + .6 * np.sin(2 * np.pi * hz(45) * t * 1.001) + .25 * np.sin(2 * np.pi * hz(50) * t * .999))
drone *= np.clip(t / 1.4, 0, 1) ** 2 * (.5 + .5 * np.clip((t - EV['land']) / .4, 0, 1))
put(lp(drone, 900), 0, .07)

# Trace: filtered noise whose band rises.
d = EV['fill'] + .4 - EV['trace']; t = tt(d)
nz = rng.standard_normal(len(t))
sh = np.zeros_like(nz)
for k, (lo, hi) in enumerate(np.linspace([800, 1600], [3000, 6000], 6)):
    seg = slice(int(k / 6 * len(t)), int((k + 1) / 6 * len(t)))
    sh[seg] = bp(nz, lo, hi)[seg]
sh *= np.sin(np.pi * np.clip(t / d, 0, 1)) ** 2
put(sh, EV['trace'], .05, -.2); put(np.roll(sh, 900), EV['trace'], .05, .2)

# Fill: upward glide.
d = .9; t = tt(d); f = hz(57) * 2 ** (t / d)
glide = np.sin(2 * np.pi * np.cumsum(f) / SR) + .3 * np.sin(4 * np.pi * np.cumsum(f) / SR)
put(edge(glide * np.sin(np.pi * t / d) ** 2), EV['fill'], .08)

# Pop: pluck.
put(bell(hz(81), .18, 1.2), EV['pop'] + .08, .22, .15)
put(bell(hz(88), .12, .8), EV['pop'] + .13, .08, -.15)

# Launch: rising whoosh.
d = EV['land'] - EV['launch'] + .15; t = tt(d); nz = rng.standard_normal(len(t))
wh = np.zeros_like(nz)
for k, c in enumerate(np.geomspace(400, 5000, 10)):
    seg = slice(int(k / 10 * len(t)), int((k + 1) / 10 * len(t)))
    wh[seg] = bp(nz, c * .7, c * 1.4)[seg]
wh *= (t / d) ** 2.2 * np.exp(-np.clip(t - (d - .15), 0, None) * 30)
put(wh, EV['launch'], .32, -.1); put(np.roll(wh, 400), EV['launch'], .32, .1)

# Land: deep hit + open D chord (D3 A3 D4 E4 F#4 A4 D5) with a shimmer.
t = tt(1.2); thump = np.sin(2 * np.pi * (45 + 40 * np.exp(-t / .05)) * t) * np.exp(-t / .35)
put(edge(thump), EV['land'], .55)
for i, (n, g) in enumerate(((50, .5), (57, .4), (62, .45), (64, .25), (66, .35), (69, .3), (74, .25))):
    put(bell(hz(n), 1.6, DUR - EV['land']), EV['land'] + i * .012, .12 * g * 2.2, (i / 3 - 1) * .5)
t = tt(DUR - EV['land']); sp = bp(rng.standard_normal(len(t)), 6000, 14000) * np.exp(-t / .5)
put(sp, EV['land'], .04)

# Word: two soft high notes.
put(bell(hz(86), .5, 2.0), EV['word'] + .1, .07, -.3)
put(bell(hz(90), .5, 2.0), EV['word'] + .32, .06, .3)

# Reverb: decaying stereo noise impulse.
ir_t = tt(2.2)
for ch in (L, R):
    ir = rng.standard_normal(len(ir_t)) * np.exp(-ir_t / .55); ir = lp(ir, 7000); ir /= np.sqrt((ir ** 2).sum())
    wet = signal.fftconvolve(ch, ir)[:N]
    ch += wet * .35

# Master fade out and normalize.
env = np.ones(N); i0 = int(EV['out'] * SR); env[i0:] = np.cos(np.linspace(0, np.pi / 2, N - i0)) ** 2
st = np.stack([L * env, R * env], 1); st /= np.abs(st).max() / .89
with wave.open(OUT, 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((st * 32767).astype('<i2').tobytes())
print('wrote', OUT, round(N / SR, 2), 's')
