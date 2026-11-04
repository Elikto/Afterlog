#!/usr/bin/env python3
"""Build the NØCTARIA web/PWA/iOS icons from the actual Android launcher vectors.

The Android drawable XML files are the one source of truth for the mascot,
foreground colours and background. This avoids a differently drawn iOS logo.
"""
from pathlib import Path
from xml.etree import ElementTree as ET
from html import escape
import cairosvg

ROOT = Path(__file__).resolve().parent
RES = ROOT.parent / "app" / "src" / "main" / "res" / "drawable"
ANDROID = "{http://schemas.android.com/apk/res/android}"

def vector_paths(filename):
    tree = ET.parse(RES / filename)
    paths = []
    for element in tree.getroot().iter("path"):
        d = element.get(ANDROID + "pathData")
        if not d:
            continue
        attrs = {"d": d, "fill": element.get(ANDROID + "fillColor", "#000000")}
        for android_attr, svg_attr in (
            ("strokeColor", "stroke"), ("strokeWidth", "stroke-width"),
            ("strokeLineCap", "stroke-linecap"), ("strokeLineJoin", "stroke-linejoin"),
            ("fillAlpha", "fill-opacity"), ("strokeAlpha", "stroke-opacity")
        ):
            value = element.get(ANDROID + android_attr)
            if value is not None:
                attrs[svg_attr] = value
        paths.append("<path " + " ".join(
            f'{name}="{escape(str(value), quote=True)}"' for name, value in attrs.items()
        ) + "/>")
    if not paths:
        raise ValueError(f"No Android paths found in {filename}")
    return paths

# iOS applies its own rounded-square home-screen mask. Use the identical Android
# foreground and background artwork without adding a different web-only mascot.
parts = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">']
parts += vector_paths("ic_afterlog_background.xml")
parts += vector_paths("ic_afterlog_foreground.xml")
parts.append("</svg>")
svg = ("\n".join(parts) + "\n").encode("utf-8")
(ROOT / "icon.svg").write_bytes(svg)
icons = ROOT / "icons"
icons.mkdir(exist_ok=True)
for name, size in [
    ("apple-touch-icon.png", 180),
    ("icon-192.png", 192),
    ("icon-512.png", 512)
]:
    cairosvg.svg2png(bytestring=svg, write_to=str(icons / name),
                    output_width=size, output_height=size)
print("NØCTARIA: Android launcher artwork reused for SVG and iOS/PWA PNG icons")
