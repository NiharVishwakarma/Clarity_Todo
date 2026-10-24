
from PIL import Image
from pathlib import Path

icon = Path("app/src/main/res/drawable/clarity_suite_icon.png")
backup = icon.with_name("clarity_suite_icon_original_backup.png")

if not icon.exists():
    raise FileNotFoundError(f"Icon not found: {icon}")

# Preserve the original artwork.
if not backup.exists():
    backup.write_bytes(icon.read_bytes())

with Image.open(icon) as source:
    print("Original dimensions:", source.size)
    resized = source.convert("RGBA").resize(
        (1024, 1024),
        Image.Resampling.LANCZOS
    )
    resized.save(icon, format="PNG", optimize=True)

with Image.open(icon) as result:
    print("New dimensions:", result.size)
    print("Color mode:", result.mode)
    assert result.size == (1024, 1024)
    assert result.mode == "RGBA"

print("SUCCESS: Icon resized to 1024x1024 RGBA.")
