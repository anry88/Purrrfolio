#!/usr/bin/env python3
"""Generate a short original instrumental loop for Purrrfolio videos."""

from __future__ import annotations

import argparse
import math
import random
import struct
import wave
from pathlib import Path


SAMPLE_RATE = 48_000
MOODS = {
    "cozy": {
        "bpm": 96,
        "root": 60,
        "progression": ((0, 4, 7, 11), (9, 12, 16, 19), (5, 9, 12, 16), (7, 11, 14, 16)),
        "melody": (12, 16, 19, 16, 14, 12, 11, 9),
        "brightness": 0.18,
    },
    "playful": {
        "bpm": 116,
        "root": 62,
        "progression": ((0, 4, 7, 9), (5, 9, 12, 16), (9, 12, 16, 19), (7, 11, 14, 18)),
        "melody": (12, 14, 16, 19, 21, 19, 16, 14),
        "brightness": 0.28,
    },
    "reveal": {
        "bpm": 108,
        "root": 57,
        "progression": ((0, 3, 7, 10), (5, 8, 12, 15), (8, 12, 15, 19), (10, 14, 17, 21)),
        "melody": (12, 15, 19, 22, 24, 22, 19, 15),
        "brightness": 0.24,
    },
}


def note_frequency(note: int) -> float:
    return 440.0 * (2.0 ** ((note - 69) / 12.0))


def envelope(position: float, duration: float, attack: float, release: float) -> float:
    if position < 0.0 or position >= duration:
        return 0.0
    attack_gain = min(1.0, position / max(attack, 0.001))
    release_gain = min(1.0, (duration - position) / max(release, 0.001))
    return attack_gain * release_gain


def add_tone(
    buffer: list[float],
    start: float,
    duration: float,
    frequency: float,
    volume: float,
    brightness: float,
    decay: float | None = None,
) -> None:
    start_sample = max(0, int(start * SAMPLE_RATE))
    end_sample = min(len(buffer), int((start + duration) * SAMPLE_RATE))
    for index in range(start_sample, end_sample):
        position = index / SAMPLE_RATE - start
        gain = envelope(position, duration, 0.02, min(0.18, duration / 3))
        if decay is not None:
            gain *= math.exp(-position * decay)
        phase = 2.0 * math.pi * frequency * position
        value = math.sin(phase) + brightness * math.sin(2.0 * phase + 0.2)
        value += brightness * 0.32 * math.sin(3.0 * phase + 0.5)
        buffer[index] += value * volume * gain


def add_kick(buffer: list[float], start: float, volume: float) -> None:
    duration = 0.22
    start_sample = int(start * SAMPLE_RATE)
    end_sample = min(len(buffer), int((start + duration) * SAMPLE_RATE))
    phase = 0.0
    for index in range(start_sample, end_sample):
        position = index / SAMPLE_RATE - start
        frequency = 90.0 - 48.0 * (position / duration)
        phase += 2.0 * math.pi * frequency / SAMPLE_RATE
        buffer[index] += math.sin(phase) * volume * math.exp(-position * 18.0)


def add_shaker(
    buffer: list[float], start: float, volume: float, randomizer: random.Random
) -> None:
    duration = 0.07
    start_sample = int(start * SAMPLE_RATE)
    end_sample = min(len(buffer), int((start + duration) * SAMPLE_RATE))
    previous = 0.0
    for index in range(start_sample, end_sample):
        position = index / SAMPLE_RATE - start
        noise = randomizer.uniform(-1.0, 1.0)
        high_pass = noise - previous * 0.82
        previous = noise
        buffer[index] += high_pass * volume * math.exp(-position * 42.0)


def compose(mood: str, duration: float, seed: int) -> list[float]:
    settings = MOODS[mood]
    sample_count = int(duration * SAMPLE_RATE)
    samples = [0.0] * sample_count
    randomizer = random.Random(seed)
    beat = 60.0 / settings["bpm"]
    bar = beat * 4.0
    progression = settings["progression"]
    melody = settings["melody"]
    root = settings["root"]

    bar_index = 0
    start = 0.0
    while start < duration:
        chord = progression[bar_index % len(progression)]
        chord_duration = min(bar, duration - start)
        for offset in chord:
            add_tone(
                samples,
                start,
                chord_duration,
                note_frequency(root + offset - 12),
                0.030,
                0.08,
            )
        add_tone(
            samples,
            start,
            min(beat * 1.7, chord_duration),
            note_frequency(root + chord[0] - 24),
            0.075,
            0.05,
            decay=1.8,
        )
        for beat_index in range(4):
            beat_start = start + beat_index * beat
            if beat_start >= duration:
                break
            if beat_index in (0, 2):
                add_kick(samples, beat_start, 0.13 if mood != "cozy" else 0.09)
            add_shaker(samples, beat_start + beat * 0.5, 0.018, randomizer)
        bar_index += 1
        start += bar

    eighth = beat / 2.0
    step = 0
    start = beat
    while start < duration - 0.3:
        note = root + melody[step % len(melody)]
        if step % 4 == 3:
            note -= 2
        add_tone(
            samples,
            start,
            min(eighth * 1.5, duration - start),
            note_frequency(note),
            0.095,
            settings["brightness"],
            decay=3.4 if mood == "playful" else 2.7,
        )
        step += 1
        start += eighth

    fade = min(0.45, duration / 5.0)
    for index, value in enumerate(samples):
        time = index / SAMPLE_RATE
        fade_in = min(1.0, time / fade)
        fade_out = min(1.0, (duration - time) / fade)
        samples[index] = value * fade_in * fade_out

    peak = max(max(abs(value) for value in samples), 0.001)
    scale = 0.74 / peak
    return [math.tanh(value * scale) * 0.86 for value in samples]


def write_stereo_wav(path: Path, samples: list[float]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(2)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        frames = bytearray()
        for sample in samples:
            integer = max(-32768, min(32767, round(sample * 32767)))
            frames.extend(struct.pack("<hh", integer, integer))
        output.writeframes(frames)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Generate original attribution-free music for a Purrrfolio Short."
    )
    parser.add_argument("--mood", choices=sorted(MOODS), default="cozy")
    parser.add_argument("--duration", type=float, default=16.0)
    parser.add_argument("--seed", type=int, default=20260925)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not 12.0 <= args.duration <= 20.0:
        raise SystemExit("Duration must be between 12 and 20 seconds.")

    samples = compose(args.mood, args.duration, args.seed)
    write_stereo_wav(args.output, samples)
    print(
        f"Generated {args.duration:.2f}s original {args.mood} instrumental: {args.output}"
    )
    print("License: Purrrfolio original composition; attribution not required.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
