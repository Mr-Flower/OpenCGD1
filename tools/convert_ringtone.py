#!/usr/bin/env python3
"""Converte un file audio nel formato della CGD1: PCM 8 bit senza segno, 8 kHz, mono, max 12 s.

Uso: convert_ringtone.py INPUT OUTPUT.pcm   (serve ffmpeg nel PATH)

Stesso trattamento che l'app applica ai file scelti dall'utente (RingtoneConverter.kt):
filtro passa-basso, picco normalizzato, dissolvenza finale se il suono viene tagliato.
"""
import array
import subprocess
import sys

SAMPLE_RATE = 8000
MAX_SAMPLES = 12 * SAMPLE_RATE
FADE_SAMPLES = SAMPLE_RATE * 3 // 10
TARGET_PEAK = 0.89  # circa -1 dBFS: margine per il piccolo altoparlante della sveglia


def main(src: str, dst: str) -> None:
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", src, "-ac", "1",
         "-af", f"lowpass=f=3600,aresample={SAMPLE_RATE}", "-f", "s16le", "-"],
        check=True, capture_output=True,
    ).stdout
    samples = array.array("h", raw)
    truncated = len(samples) > MAX_SAMPLES
    samples = samples[:MAX_SAMPLES]
    peak = max((abs(s) for s in samples), default=0) or 1
    gain = TARGET_PEAK * 32767 / peak
    out = bytearray(len(samples))
    for i, s in enumerate(samples):
        v = s * gain
        if truncated and i >= len(samples) - FADE_SAMPLES:
            v *= (len(samples) - i) / FADE_SAMPLES
        out[i] = max(0, min(255, round(v / 256) + 128))
    with open(dst, "wb") as f:
        f.write(out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
