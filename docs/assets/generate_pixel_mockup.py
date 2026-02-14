#!/usr/bin/env python3
"""
Generate a Google Pixel-style phone mockup PNG with a transparent screen area.

The output has:
  - A dark near-black phone body (#1A1C19) with rounded corners
  - Thin bezels (~28px sides, ~60px top, ~50px bottom)
  - A fully transparent screen cutout (1080x2340)
  - A small camera punch-hole at top center
  - Subtle side-button details (power + volume)
  - A very thin highlight edge to give a slight 3D look
"""

from PIL import Image, ImageDraw

# -- Dimensions ---------------------------------------------------------------
SCREEN_W = 1080
SCREEN_H = 2340

BEZEL_LEFT = 28
BEZEL_RIGHT = 28
BEZEL_TOP = 60
BEZEL_BOTTOM = 50

PHONE_W = BEZEL_LEFT + SCREEN_W + BEZEL_RIGHT          # 1136
PHONE_H = BEZEL_TOP + SCREEN_H + BEZEL_BOTTOM          # 2450

# Canvas: a bit of padding around the phone for the side buttons
PAD_LEFT = 6
PAD_RIGHT = 6
PAD_TOP = 4
PAD_BOTTOM = 4
CANVAS_W = PAD_LEFT + PHONE_W + PAD_RIGHT
CANVAS_H = PAD_TOP + PHONE_H + PAD_BOTTOM

# Phone body top-left on the canvas
PX = PAD_LEFT
PY = PAD_TOP

# Corner radii
OUTER_R = 58
INNER_R = 40

# Colors (RGBA)
BODY_COLOR = (26, 28, 25, 255)          # #1A1C19 near-black
EDGE_HIGHLIGHT = (60, 62, 58, 255)      # subtle lighter edge
CAMERA_COLOR = (18, 19, 17, 255)        # slightly darker for lens
CAMERA_RING = (45, 47, 43, 255)         # metallic ring around lens
TRANSPARENT = (0, 0, 0, 0)
BUTTON_COLOR = (35, 37, 33, 255)        # side buttons


def draw_rounded_rect(draw, x0, y0, x1, y1, r, fill):
    """Draw a filled rounded rectangle."""
    if hasattr(draw, "rounded_rectangle"):
        draw.rounded_rectangle([x0, y0, x1, y1], radius=r, fill=fill)
    else:
        draw.rectangle([x0 + r, y0, x1 - r, y1], fill=fill)
        draw.rectangle([x0, y0 + r, x1, y1 - r], fill=fill)
        draw.ellipse([x0, y0, x0 + 2*r, y0 + 2*r], fill=fill)
        draw.ellipse([x1 - 2*r, y0, x1, y0 + 2*r], fill=fill)
        draw.ellipse([x1 - 2*r, y1 - 2*r, x1, y1], fill=fill)
        draw.ellipse([x0, y1 - 2*r, x0 + 2*r, y1], fill=fill)


def main():
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), TRANSPARENT)
    draw = ImageDraw.Draw(img)

    # 1. Outer edge highlight (slightly larger, lighter color) ----------------
    draw_rounded_rect(
        draw,
        PX - 1, PY - 1,
        PX + PHONE_W, PY + PHONE_H,
        OUTER_R + 1,
        EDGE_HIGHLIGHT,
    )

    # 2. Main phone body ------------------------------------------------------
    draw_rounded_rect(
        draw,
        PX, PY,
        PX + PHONE_W - 1, PY + PHONE_H - 1,
        OUTER_R,
        BODY_COLOR,
    )

    # 3. Screen cutout (transparent) ------------------------------------------
    screen_x0 = PX + BEZEL_LEFT
    screen_y0 = PY + BEZEL_TOP
    screen_x1 = screen_x0 + SCREEN_W - 1
    screen_y1 = screen_y0 + SCREEN_H - 1

    # Create a mask for the screen area, then erase those pixels
    mask = Image.new("L", (CANVAS_W, CANVAS_H), 0)
    mask_draw = ImageDraw.Draw(mask)
    draw_rounded_rect(
        mask_draw,
        screen_x0, screen_y0,
        screen_x1, screen_y1,
        INNER_R,
        255,
    )
    # Set alpha to 0 everywhere the mask is white (the screen area)
    img_data = img.load()
    mask_data = mask.load()
    for y in range(screen_y0, min(screen_y1 + 1, CANVAS_H)):
        for x in range(screen_x0, min(screen_x1 + 1, CANVAS_W)):
            if mask_data[x, y] > 0:
                img_data[x, y] = (0, 0, 0, 0)

    # 4. Camera punch-hole (top center of screen area) ------------------------
    cam_diameter = 22
    cam_ring_diameter = 28
    cam_cx = screen_x0 + SCREEN_W // 2
    cam_cy = screen_y0 + 42  # slightly below top edge of screen

    # Ring
    draw.ellipse(
        [
            cam_cx - cam_ring_diameter // 2,
            cam_cy - cam_ring_diameter // 2,
            cam_cx + cam_ring_diameter // 2,
            cam_cy + cam_ring_diameter // 2,
        ],
        fill=CAMERA_RING,
    )
    # Lens (darker center)
    draw.ellipse(
        [
            cam_cx - cam_diameter // 2,
            cam_cy - cam_diameter // 2,
            cam_cx + cam_diameter // 2,
            cam_cy + cam_diameter // 2,
        ],
        fill=CAMERA_COLOR,
    )
    # Tiny specular highlight on lens
    spec_r = 3
    draw.ellipse(
        [
            cam_cx - spec_r - 2,
            cam_cy - spec_r - 2,
            cam_cx - spec_r + 2,
            cam_cy - spec_r + 2,
        ],
        fill=(80, 82, 78, 180),
    )

    # 5. Side buttons (right side: power + volume) ----------------------------
    pw_x = PX + PHONE_W - 1
    pw_y = PY + 380
    pw_h = 90
    pw_w = 5
    draw_rounded_rect(
        draw,
        pw_x, pw_y,
        pw_x + pw_w, pw_y + pw_h,
        2,
        BUTTON_COLOR,
    )

    # Volume rocker (right side, below power)
    vol_x = pw_x
    vol_y = pw_y + pw_h + 40
    vol_h = 160
    draw_rounded_rect(
        draw,
        vol_x, vol_y,
        vol_x + pw_w, vol_y + vol_h,
        2,
        BUTTON_COLOR,
    )

    # 6. Thin inner bezel border to define screen edge crisply ----------------
    outline_mask = Image.new("RGBA", (CANVAS_W, CANVAS_H), TRANSPARENT)
    outline_draw = ImageDraw.Draw(outline_mask)
    # Slightly larger rounded rect
    draw_rounded_rect(
        outline_draw,
        screen_x0 - 1, screen_y0 - 1,
        screen_x1 + 1, screen_y1 + 1,
        INNER_R + 1,
        (10, 12, 9, 200),
    )
    # Punch out the screen area again so only the 1px border remains
    draw_rounded_rect(
        outline_draw,
        screen_x0, screen_y0,
        screen_x1, screen_y1,
        INNER_R,
        TRANSPARENT,
    )
    img = Image.alpha_composite(img, outline_mask)

    # 7. Save -----------------------------------------------------------------
    output_path = "/home/user/selfcontrol-android/docs/assets/pixel_phone_mockup.png"
    img.save(output_path, "PNG")
    print(f"Saved mockup to {output_path}")
    print(f"  Canvas size : {CANVAS_W} x {CANVAS_H}")
    print(f"  Phone body  : {PHONE_W} x {PHONE_H}")
    print(f"  Screen area : {SCREEN_W} x {SCREEN_H}")
    print(f"  Outer radius: {OUTER_R}px,  Inner radius: {INNER_R}px")


if __name__ == "__main__":
    main()
