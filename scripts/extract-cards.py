#!/usr/bin/env python3
"""Extract individual card PNGs from the starter sprite sheet."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs" / "source" / "card-sprite-sheet.png"
OUTPUT_DIR = ROOT / "assets" / "cards"
STATIC_DIR = ROOT / "src" / "main" / "resources" / "static" / "assets" / "cards"

CARDS = [
    ("sleepy", 0, 0),
    ("librarian", 1, 0),
    ("rainy", 2, 0),
    ("baker", 3, 0),
    ("blossom", 4, 0),
    ("wanderer", 0, 1),
    ("astronaut", 1, 1),
    ("night-watcher", 2, 1),
    ("fisher", 3, 1),
    ("boxie", 4, 1),
]


def crop_cell(image: Image.Image, column: int, row: int) -> Image.Image:
    width, height = image.size
    cell_w = width / 5
    cell_h = height / 2
    left = int(column * cell_w + cell_w * 0.04)
    top = int(row * cell_h + cell_h * 0.04)
    right = int((column + 1) * cell_w - cell_w * 0.04)
    bottom = int((row + 1) * cell_h - cell_h * 0.04)
    return image.crop((left, top, right, bottom))


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Missing source image: {SOURCE}")

    image = Image.open(SOURCE).convert("RGBA")
    for output_dir in (OUTPUT_DIR, STATIC_DIR):
        output_dir.mkdir(parents=True, exist_ok=True)

    for slug, column, row in CARDS:
        cropped = crop_cell(image, column, row)
        for output_dir in (OUTPUT_DIR, STATIC_DIR):
            target = output_dir / f"{slug}.png"
            cropped.save(target, format="PNG", optimize=True)
            print(f"Saved {target}")


if __name__ == "__main__":
    main()
