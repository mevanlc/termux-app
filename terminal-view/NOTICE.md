# Geometric glyph rendering

`TerminalGlyphs.java` and `GlyphMappings.java` adapt the glyph repertoire,
coordinate mappings, and selected geometric constructions from
[Kitty's `kitty/decorations.c`](https://github.com/kovidgoyal/kitty/blob/31c16b37b/kitty/decorations.c)
and [`kitty/fonts.c`](https://github.com/kovidgoyal/kitty/blob/31c16b37b/kitty/fonts.c),
snapshot `31c16b37b`.

Copyright (C) 2024 Kovid Goyal (decorations); Copyright (C) 2017 Kovid Goyal (fonts).
These adaptations are distributed under the
[GNU General Public License, version 3 only](https://www.gnu.org/licenses/gpl-3.0.html).
They use Android paths and paints rather than Kitty's native rasterizer; their
coverage matches this snapshot, but their output is not a pixel-for-pixel port.

The box-drawing arm table encodes the directional weights in Unicode character
names. No font outlines or font files are included. The existing Apache 2.0
license on inherited Terminal Emulator for Android code remains unchanged.
