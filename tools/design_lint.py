#!/usr/bin/env python3
"""Design lint for Stream Master. Run from the project root:  python3 tools/design_lint.py
Fails (exit 1) when a governed screen breaks the rules in DESIGN.md."""
import glob, os, re, sys

RES = "app/src/main/res"
SCALE = {12, 14, 16, 20, 24, 28}
# screens governed by the type scale (Home, player, drawer and legacy activities are listed in DESIGN.md as "not migrated")
GOVERNED = ["activity_details", "fragment_category", "fragment_favorites", "fragment_search", "item_cast",
            "item_category_card", "item_review", "item_grid_movie", "item_home_movie", "item_movie",
            "fragment_series", "item_episode", "activity_series_details"]
GLYPHS = re.compile(r"[⌕✓›‹×★☆♥⚙✕✖↺▼▲]")
EMOJI = re.compile("[\U0001F300-\U0001FAFF\u2600-\u26FF]")
problems = []

for name in GOVERNED:
    path = f"{RES}/layout/{name}.xml"
    text = open(path, encoding="utf-8").read()
    for size in re.findall(r'android:textSize="(\d+)sp"', text):
        if int(size) not in SCALE:
            problems.append(f"{name}: textSize {size}sp is not on the scale {sorted(SCALE)}")

for path in glob.glob(f"{RES}/layout/*.xml"):
    if os.path.basename(path).startswith("activity_") and os.path.basename(path) in (
            "activity_category.xml", "activity_favorites.xml", "activity_search.xml"):
        continue  # legacy, never started
    text = open(path, encoding="utf-8").read()
    for colour in re.findall(r'android:textColor="(#[0-9A-Fa-f]{6,8})"', text):
        if os.path.basename(path) not in ("item_hero.xml", "item_continue.xml", "item_shelf.xml", "fragment_home.xml"):
            problems.append(f"{os.path.basename(path)}: hard-coded textColor {colour} (use a colour token)")
    for m in GLYPHS.findall(text):
        problems.append(f"{os.path.basename(path)}: glyph '{m}' used as an icon (use a vector drawable)")

for path in glob.glob(f"{RES}/values*/strings.xml"):
    text = open(path, encoding="utf-8").read()
    if EMOJI.search(text):
        problems.append(f"{path}: emoji in UI copy")
    if GLYPHS.search(text):
        problems.append(f"{path}: arrow/star glyph in UI copy")

# outlines are reserved for the poster frame, dividers and selected states
ALLOWED_STROKE = {"bg_card_frame.xml"}
for path in glob.glob(f"{RES}/drawable/bg_*.xml"):
    base = os.path.basename(path)
    if base in ALLOWED_STROKE:
        continue
    if base in ("bg_panel.xml", "bg_tag.xml", "bg_filter_chip.xml", "bg_dialog.xml", "bg_circle_panel.xml"):
        if "<stroke" in open(path, encoding="utf-8").read():
            problems.append(f"{base}: tonal surfaces must not carry an outline")

if problems:
    print("\n".join(problems)); print(f"\n{len(problems)} problem(s)"); sys.exit(1)
print("design lint: OK")
