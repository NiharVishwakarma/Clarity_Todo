
from PIL import Image
from pathlib import Path

source_path = Path("app/src/main/res/drawable/clarity_suite_icon.png")
preview_path = Path("clarity_suite_icon_padded_preview.png")

# Reduce the artwork to 80% and center it on a transparent 1024x1024 canvas.
canvas_size = 1024
artwork_scale = 0.80
new_size = round(canvas_size * artwork_scale)

with Image.open(source_path) as source:
    source = source.convert("RGBA")

    if source.size != (1024, 1024):
        raise ValueError(f"Expected 1024x1024, found {source.size}")

    artwork = source.resize(
        (new_size, new_size),
        Image.Resampling.LANCZOS
    )

    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    offset = ((canvas_size - new_size) // 2,) * 2
    canvas.alpha_composite(artwork, dest=offset)
    canvas.save(preview_path, format="PNG", optimize=True)

print(f"Preview created: {preview_path}")
print(f"Canvas: {canvas_size}x{canvas_size}")
print(f"Artwork: {new_size}x{new_size}")
print("Original icon has not been changed.")