#!/usr/bin/env python3
"""Converte un file audio nel formato della CGD1: PCM 8 bit senza segno, 8 kHz, mono, max 12 s.

Uso: convert_ringtone.py INPUT OUTPUT.pcm   (serve ffmpeg nel PATH)

Stesso algoritmo di PcmProcessing.kt, che l'app applica ai file scelti dall'utente:
- passa-alto a 300 Hz: l'altoparlante della sveglia non riproduce i bassi, che consumano solo potenza
- passa-basso del 4° ordine a 3,5 kHz contro l'aliasing a 8 kHz
- compressore (soglia -12 dBFS, rapporto 4:1, attacco istantaneo) per alzare il volume percepito come le suonerie originali
- normalizzazione del picco con un tetto al volume medio pari alla suoneria originale più forte, dissolvenze agli estremi
"""
import array
import math
import subprocess
import sys

SAMPLE_RATE = 8000
MAX_SAMPLES = 12 * SAMPLE_RATE
HIGH_PASS_HZ = 300.0
LOW_PASS_HZ = 3500.0
THRESHOLD = 0.25
RATIO = 4.0
RELEASE_MS = 150.0
TARGET_PEAK = 0.85
MAX_RMS = 0.28  # volume medio della suoneria Qingping più forte (Telephone)
FADE_IN_MS = 5
FADE_OUT_MS = 300


def biquad(samples, b0, b1, b2, a1, a2):
    out = [0.0] * len(samples)
    x1 = x2 = y1 = y2 = 0.0
    for i, x in enumerate(samples):
        y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        out[i] = y
        x2, x1, y2, y1 = x1, x, y1, y
    return out


def low_pass(samples, rate, cutoff, q):
    w0 = 2 * math.pi * cutoff / rate
    alpha = math.sin(w0) / (2 * q)
    c = math.cos(w0)
    a0 = 1 + alpha
    return biquad(samples, (1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)


def high_pass(samples, rate, cutoff, q):
    w0 = 2 * math.pi * cutoff / rate
    alpha = math.sin(w0) / (2 * q)
    c = math.cos(w0)
    a0 = 1 + alpha
    return biquad(samples, (1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)


def resample(samples, src, dst):
    if src == dst or not samples:
        return samples
    n = len(samples) * dst // src
    step = src / dst
    out = [0.0] * n
    for i in range(n):
        pos = i * step
        j = int(pos)
        frac = pos - j
        a = samples[j]
        b = samples[min(j + 1, len(samples) - 1)]
        out[i] = a + (b - a) * frac
    return out


def compress(samples, rate):
    # Attacco istantaneo: un attacco lento lascerebbe passare il primo picco e la normalizzazione si regolerebbe su quello.
    release = math.exp(-1 / (rate * RELEASE_MS / 1000))
    env = 0.0
    out = [0.0] * len(samples)
    for i, x in enumerate(samples):
        env = max(abs(x), release * env)
        gain = (THRESHOLD * (env / THRESHOLD) ** (1 / RATIO)) / env if env > THRESHOLD else 1.0
        out[i] = x * gain
    return out


def limit_rms(samples, maximum):
    if not samples:
        return samples
    rms = math.sqrt(sum(s * s for s in samples) / len(samples))
    return [s * maximum / rms for s in samples] if rms > maximum else samples


def normalize(samples, target):
    peak = max((abs(s) for s in samples), default=0.0)
    return [s * target / peak for s in samples] if peak > 0 else samples


def process(samples, rate):
    # Butterworth del 4° ordine = due biquad con Q 0.5412 e 1.3066.
    x = high_pass(samples, rate, HIGH_PASS_HZ, 0.7071)
    if rate > 2 * LOW_PASS_HZ:
        x = low_pass(low_pass(x, rate, LOW_PASS_HZ, 0.5412), rate, LOW_PASS_HZ, 1.3066)
    x = resample(x, rate, SAMPLE_RATE)
    truncated = len(x) > MAX_SAMPLES
    x = x[:MAX_SAMPLES]
    x = limit_rms(normalize(compress(normalize(x, 1.0), SAMPLE_RATE), TARGET_PEAK), MAX_RMS)
    fade_in = SAMPLE_RATE * FADE_IN_MS // 1000
    fade_out = SAMPLE_RATE * FADE_OUT_MS // 1000 if truncated else 0
    out = bytearray(len(x))
    for i, v in enumerate(x):
        if i < fade_in:
            v *= i / fade_in
        remaining = len(x) - i
        if remaining <= fade_out:
            v *= remaining / fade_out
        out[i] = max(0, min(255, round(v * 127) + 128))
    return bytes(out)


def main(src, dst):
    rate = 44100
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", src, "-ac", "1", "-ar", str(rate), "-f", "s16le", "-"],
        check=True, capture_output=True,
    ).stdout
    samples = [s / 32768 for s in array.array("h", raw)]
    with open(dst, "wb") as f:
        f.write(process(samples, rate))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
