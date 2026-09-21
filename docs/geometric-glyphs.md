# Geometric terminal glyphs

The built-in renderer covers the following 1,098 code points, matching the
Kitty `BOX_FONT` dispatch at `31c16b37b`. All ranges are inclusive. Other text
continues to use the selected font and Android's font fallback.

| Family | Code points |
| --- | --- |
| Box drawing | U+2500–U+257F |
| Standard blocks and shades | U+2580–U+259F |
| Selected geometric shapes | U+25C9, U+25CB, U+25CF, U+25D6–U+25D7, U+25DC–U+25E5 |
| Braille patterns (including blank U+2800) | U+2800–U+28FF |
| Powerline separators | U+E0B0–U+E0BF, U+E0D6–U+E0D7 |
| Fira Code progress bars and spinners | U+EE00–U+EE0B |
| Branch/commit graph symbols | U+F5D0–U+F60D |
| Legacy computing mosaics, fractional blocks, shading, diagonal connectors | U+1FB00–U+1FBAE |
| Additional blocks, diagonals, and circle portions | U+1FBCE–U+1FBEF |
| Block octants | U+1CD00–U+1CDE5 |
| Additional box drawing, separated quadrants, and circle arcs | U+1CC1B–U+1CC3F |
| Offset box junctions | U+1CE16–U+1CE19 |
| Separated sextants, sixteenth blocks, quarter-strip partial fills | U+1CE51–U+1CEAF |

Cell boundaries are rounded from their absolute grid positions. Each glyph
subdivides the resulting integer cell bounds, so blocks meet even when zoom
produces a fractional character width. Rectangular fills use opaque pixel
edges; curves and diagonals use antialiasing. Separated quadrants and sextants
intentionally include gaps. Geometry is independent of foreground/background
colors and cached per renderer, character, and integer cell dimensions, with
at most 512 cached glyphs. Font/size changes recreate the renderer.

Colors, brightness, reverse video, selection, cursor, dim, underline, and
strike-through use the normal terminal styling. Bold retains the usual bright
palette selection but does not smear shapes; italic does not skew them.
There is no new setting or font dependency.

## Validation

`./gradlew :terminal-view:testDebugUnitTest :app:testDebugUnitTest --tests 'com.termux.view.*'`
checks repertoire coverage, cell clipping at small and odd dimensions,
complementary block tiling, fractional-zoom seams, and terminal styling.
`TerminalGlyphsRenderingTest` also writes five labeled PNG atlases under
`app/build/glyph-atlas/` for visual review. These are build artifacts.

See [NOTICE.md](../terminal-view/NOTICE.md) for upstream provenance and licensing.
