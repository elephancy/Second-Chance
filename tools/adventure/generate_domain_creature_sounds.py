"""Synthesize five original creature cues and encode reproducible Ogg Vorbis.

No sampled, downloaded, or AI-generated audio is used. All PCM samples are
calculated below at 24 kHz with a fixed random seed. The checked-in Ogg files
were encoded with FFmpeg 9.0.1 / libvorbis using the command in encode().
Pass --ffmpeg PATH when FFmpeg is not on PATH; --check compares exact bytes.
"""

from __future__ import annotations

import argparse
import math
import random
import shutil
import struct
import subprocess
from pathlib import Path


OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/gems/sounds"
RATE = 24_000


def samples(family: str) -> bytes:
    length = {"paper": 0.29, "clockwork": 0.36, "breath": 0.32,
              "stone": 0.30, "reed": 0.42}[family]
    noise = random.Random(0xC4E9 + sum(map(ord, family)))
    previous_noise = 0.0
    values = []
    for index in range(round(RATE * length)):
        t = index / RATE
        raw_noise = noise.uniform(-1.0, 1.0)
        previous_noise = previous_noise * 0.76 + raw_noise * 0.24
        if family == "paper":
            flutter = max(0.0, math.sin(2 * math.pi * 17 * t))
            envelope = (1 - math.exp(-35 * t)) * math.exp(-8 * t)
            value = envelope * (0.45 * previous_noise * flutter
                                + 0.16 * math.sin(2 * math.pi * 430 * t))
        elif family == "clockwork":
            value = 0.0
            for onset, pitch in ((0.0, 970), (0.11, 1240), (0.23, 790)):
                age = t - onset
                if age >= 0:
                    decay = math.exp(-35 * age)
                    value += decay * (0.34 * math.sin(2 * math.pi * pitch * age)
                                      + 0.16 * math.sin(2 * math.pi * pitch * 1.91 * age))
        elif family == "breath":
            envelope = math.sin(math.pi * min(1.0, t / length)) ** 1.5
            frequency = 560 + 420 * t / length
            phase = 2 * math.pi * (560 * t + 210 * t * t / length)
            value = envelope * (0.30 * math.sin(phase)
                                + 0.10 * previous_noise
                                + 0.06 * math.sin(phase * 2 + frequency / 500))
        elif family == "reed":
            # Two watery notes with a soft, wind-blown reed hiss between them.
            pulse = max(0.0, math.sin(2 * math.pi * 5.2 * t)) ** 2
            envelope = (1 - math.exp(-70 * t)) * math.exp(-3.2 * t)
            phase = 2 * math.pi * (380 * t + 145 * t * t / length)
            value = envelope * (0.32 * pulse * math.sin(phase)
                                + 0.12 * previous_noise
                                + 0.09 * math.sin(phase * 1.5))
        else:
            envelope = (1 - math.exp(-150 * t)) * math.exp(-15 * t)
            phase = 2 * math.pi * (220 * t - 105 * t * t / length)
            value = envelope * (0.49 * math.sin(phase) + 0.15 * raw_noise)
        values.append(struct.pack("<h", max(-32768, min(32767, round(value * 32767)))))
    return b"".join(values)


def encode(ffmpeg: str, pcm: bytes) -> bytes:
    command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin",
               "-f", "s16le", "-ar", str(RATE), "-ac", "1", "-i", "pipe:0",
               "-map_metadata", "-1", "-fflags", "+bitexact",
               "-flags:a", "+bitexact", "-c:a", "libvorbis", "-q:a", "3",
               "-f", "ogg", "pipe:1"]
    result = subprocess.run(command, input=pcm, capture_output=True, check=True)
    if not result.stdout.startswith(b"OggS"):
        raise RuntimeError("FFmpeg did not return an Ogg stream")
    return result.stdout


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg", default=shutil.which("ffmpeg"))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if not args.ffmpeg:
        parser.error("FFmpeg/libvorbis is required; pass --ffmpeg PATH")
    for family in ("paper", "clockwork", "breath", "stone", "reed"):
        path = OUT / f"creature_{family}.ogg"
        sound = encode(args.ffmpeg, samples(family))
        if args.check:
            if not path.is_file() or path.read_bytes() != sound:
                raise SystemExit(f"Cue differs: {path}")
        else:
            path.write_bytes(sound)
    print(f"{'Verified' if args.check else 'Generated'} five original creature cues")


if __name__ == "__main__":
    main()
