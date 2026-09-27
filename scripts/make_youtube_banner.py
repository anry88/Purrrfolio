#!/usr/bin/env python3
import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


TARGET_SIZE = (2048, 1152)
TITLE = "Purrrfolio"
SUBTITLE = "Collect cute cat cards on Telegram"


def font(size, bold=False):
    candidates = [
        "/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Supplemental/Verdana Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Verdana.ttf",
    ]
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            return ImageFont.truetype(str(path), size=size)
    return ImageFont.load_default(size=size)


def fit_cover(image, size):
    source_w, source_h = image.size
    target_w, target_h = size
    scale = max(target_w / source_w, target_h / source_h)
    resized = image.resize((round(source_w * scale), round(source_h * scale)), Image.Resampling.LANCZOS)
    left = (resized.width - target_w) // 2
    top = (resized.height - target_h) // 2
    return resized.crop((left, top, left + target_w, top + target_h))


def centered_text(draw, xy, text, text_font, fill, stroke_fill=None, stroke_width=0):
    box = draw.textbbox((0, 0), text, font=text_font, stroke_width=stroke_width)
    width = box[2] - box[0]
    draw.text(
        (xy[0] - width / 2, xy[1]),
        text,
        font=text_font,
        fill=fill,
        stroke_fill=stroke_fill,
        stroke_width=stroke_width,
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    base = fit_cover(Image.open(args.source).convert("RGB"), TARGET_SIZE)
    overlay = Image.new("RGBA", TARGET_SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    cx = TARGET_SIZE[0] // 2
    card_w, card_h = 1040, 320
    card_x = cx - card_w // 2
    card_y = 424

    shadow = Image.new("RGBA", TARGET_SIZE, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle(
        (card_x + 10, card_y + 18, card_x + card_w + 10, card_y + card_h + 18),
        radius=42,
        fill=(88, 56, 37, 92),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    overlay.alpha_composite(shadow)

    draw.rounded_rectangle(
        (card_x, card_y, card_x + card_w, card_y + card_h),
        radius=42,
        fill=(255, 249, 232, 218),
        outline=(255, 212, 112, 210),
        width=4,
    )

    title_font = font(148, bold=True)
    subtitle_font = font(48, bold=True)
    centered_text(
        draw,
        (cx, card_y + 54),
        TITLE,
        title_font,
        fill=(64, 45, 35, 255),
        stroke_fill=(255, 255, 255, 200),
        stroke_width=3,
    )
    centered_text(
        draw,
        (cx, card_y + 218),
        SUBTITLE,
        subtitle_font,
        fill=(75, 91, 99, 255),
    )

    result = Image.alpha_composite(base.convert("RGBA"), overlay).convert("RGB")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    result.save(args.output, quality=91, optimize=True, progressive=True)


if __name__ == "__main__":
    main()
