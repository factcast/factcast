"""Render the recorded JMH comparison as a presentation slide (requires Pillow)."""

import csv
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


HERE = Path(__file__).parent
RESULTS = HERE / "results"
ORDER = ["jackson", "binary", "binary-lz4", "fury", "fury-lz4", "fury-snappy"]
LABELS = {
    "jackson": "Jackson JSON",
    "binary": "Binary Jackson",
    "binary-lz4": "Binary + LZ4",
    "fury": "Fury",
    "fury-lz4": "Fury + LZ4",
    "fury-snappy": "Fury + Snappy",
}
NAVY = "#19344A"
MUTED = "#657987"
CORAL = "#E5765D"
TEAL = "#138A83"
GRID = "#E9EEF0"


def font(size, bold=False):
    weight = "Heavy" if bold else "Regular"
    return ImageFont.truetype(f"/usr/share/fonts/truetype/lato/Lato-{weight}.ttf", size)


def fmt_time(value):
    return f"{value:.2f}" if value < 10 else f"{value:.1f}"


def fmt_size(value):
    return f"{value / 1024:.1f} KB" if value >= 1024 else f"{value} B"


with (RESULTS / "snapshot-serialization-jdk21.json").open() as file:
    records = json.load(file)

times = {}
for record in records:
    count = int(record["params"]["entryCount"])
    name = record["params"]["serializerName"]
    operation = record["benchmark"].split(".")[-1]
    times[count, name, operation] = record["primaryMetric"]["score"]

with (RESULTS / "serialized-sizes.csv").open(newline="") as file:
    sizes = {
        (int(row["entries"]), row["serializer"]): int(row["bytes"])
        for row in csv.DictReader(file)
    }

image = Image.new("RGB", (2000, 1160), "#F4F7F6")
draw = ImageDraw.Draw(image)

# A light shadow gives each panel a clear edge on slides with white backgrounds.
shadow = Image.new("RGBA", image.size)
shadow_draw = ImageDraw.Draw(shadow)
for left in (70, 1020):
    shadow_draw.rounded_rectangle((left, 227, left + 910, 1035), 28, fill=(31, 52, 65, 35))
shadow = shadow.filter(ImageFilter.GaussianBlur(18))
image.paste(shadow, (0, 0), shadow)
draw = ImageDraw.Draw(image)

draw.text((76, 46), "Snapshot serializer performance", font=font(59, True), fill=NAVY)
draw.text(
    (78, 128),
    "Same projection  ·  same in-memory cache  ·  lower latency is better",
    font=font(26),
    fill=MUTED,
)
draw.rounded_rectangle((1610, 69, 1928, 117), 22, fill="#E5EEEB")
draw.text((1640, 79), "FACTCAST  /  JMH 1.37", font=font(19, True), fill=TEAL)


def draw_panel(left, count):
    top = 222
    draw.rounded_rectangle((left, top, left + 910, 1030), 28, fill="white")
    draw.text((left + 34, top + 29), f"{count:,} entries", font=font(39, True), fill=NAVY)
    draw.text((left + 35, top + 81), "Average time per snapshot operation", font=font(21), fill=MUTED)

    legend_y = top + 129
    draw.rounded_rectangle((left + 35, legend_y + 5, left + 54, legend_y + 24), 5, fill=CORAL)
    draw.text((left + 65, legend_y), "Serialize + store", font=font(19), fill=NAVY)
    draw.rounded_rectangle((left + 245, legend_y + 5, left + 264, legend_y + 24), 5, fill=TEAL)
    draw.text((left + 275, legend_y), "Find + deserialize", font=font(19), fill=NAVY)
    draw.text((left + 752, legend_y), "Payload", font=font(19, True), fill=MUTED)

    plot_left, plot_width = left + 225, 425
    scale_min, scale_max, ticks = (
        (0.5, 50, (0.5, 1, 2, 5, 10, 20, 50))
        if count == 16
        else (20, 3000, (20, 50, 100, 200, 500, 1000, 3000))
    )

    def plot_x(value):
        fraction = math.log(value / scale_min) / math.log(scale_max / scale_min)
        return round(plot_left + fraction * plot_width)

    header_y = top + 178
    for tick in ticks:
        label = str(tick).removesuffix(".0")
        x = plot_x(tick)
        draw.text((x - 7, header_y), label, font=font(16), fill=MUTED)

    rows_top = top + 215
    row_height = 86
    for index, name in enumerate(ORDER):
        y = rows_top + index * row_height
        if index % 2 == 0:
            draw.rounded_rectangle((left + 23, y - 8, left + 887, y + 79), 12, fill="#F8FAFA")
        draw.text((left + 36, y + 17), LABELS[name], font=font(22, True), fill=NAVY)
        draw.text((left + 755, y + 30), fmt_size(sizes[count, name]), font=font(22, True), fill=NAVY)

        for tick in ticks:
            x = plot_x(tick)
            draw.line((x, y + 10, x, y + 70), fill=GRID, width=2)

        for offset, operation, color in (
            (17, "serializeAndStore", CORAL),
            (47, "findAndDeserialize", TEAL),
        ):
            score = times[count, name, operation]
            x = plot_x(score)
            dot_y = y + offset + 7
            draw.line((plot_left, dot_y, x, dot_y), fill=color, width=3)
            draw.ellipse((x - 8, dot_y - 8, x + 8, dot_y + 8), fill=color)
            fastest = min(times[count, item, operation] for item in ORDER)
            draw.text(
                (left + 667, y + offset - 6),
                fmt_time(score),
                font=font(19, score == fastest),
                fill=color if score == fastest else NAVY,
            )

    draw.line((left + 34, top + 791, left + 876, top + 791), fill=GRID, width=2)
    draw.text(
        (left + 36, top + 755),
        "Time axis is logarithmic  ·  µs per operation",
        font=font(18),
        fill=MUTED,
    )


draw_panel(70, 16)
draw_panel(1020, 1024)

draw.text(
    (78, 1070),
    "JDK 21  ·  2 JVM forks  ·  2 × 1 s warmup + 3 × 1 s measurement per fork  ·  single thread",
    font=font(21),
    fill=MUTED,
)
draw.text(
    (78, 1104),
    "Payload is serialized projection size; times include in-memory cache access. Source: JMH JSON in results/.",
    font=font(20),
    fill=MUTED,
)

image.save(RESULTS / "snapshot-serializer-comparison.png", optimize=True)
