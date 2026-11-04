#!/usr/bin/env python3
"""Generate iOS-friendly PNG icons from the NØCTARIA vector using CairoSVG."""
from pathlib import Path
import cairosvg
root=Path(__file__).resolve().parent
(root/'icons').mkdir(exist_ok=True)
svg=(root/'icon.svg').read_bytes()
for name,size in [('apple-touch-icon.png',180),('icon-192.png',192),('icon-512.png',512)]:
    cairosvg.svg2png(bytestring=svg,write_to=str(root/'icons'/name),output_width=size,output_height=size)
print('PWA icons ready')
