# a termux-app fork

This is a fork of [Termux](https://termux.dev), an Android terminal application
and Linux environment. See the
[upstream repository](https://github.com/termux/termux-app) for general
documentation, installation instructions, and community links.

## about this fork

This fork is based on a recent upstream `master` (June 2026, post-`v0.118.3`)
and adds:

- terminal graphics: Sixel and iTerm2 inline image rendering;
- built-in rendering of Unicode octant, sextant, and block glyphs;
- real bold/italic terminal font variants via `font-bold.ttf`,
  `font-italic.ttf`, and `font-bold-italic.ttf`;
- extra-keys toolbar improvements: any number of named key panels loaded from a
  json file, double-tap modifier locking, swipe-up key cancellation, and a
  scroll-lock icon;
- a swipe-navigable history for the toolbar text input;
- per-session font zoom and a configurable minimum zoom size;
- clipboard image paste, a "Paste with Newlines" action, and an "Open URL"
  text-selection action;
- a terminal color brightness setting;
- XTVERSION reporting with a configurable product name; and
- assorted terminal-emulator fixes (larger OSC 52 clipboard writes, wide-row
  and line-wrap corrections).

Everything else behaves like upstream Termux unless noted below.

### terminal graphics

The terminal emulator renders inline images from two protocols:

- **Sixel** (`DCS q`, with or without the optional `P1;P2;P3` parameters,
  which are accepted but ignored). Sixel support is advertised in the primary
  device attributes response (attribute `4`). RGB color definitions are
  supported; HLS color definitions are ignored.
- **iTerm2 inline images** (`OSC 1337`): `File=`, `MultipartFile=`/`FilePart=`/
  `FileEnd`, and the `ReportCellSize` query. Recognized `File=` arguments are
  `inline`, `preserveAspectRatio`, `width`, and `height` (in `px`, `%`, or
  cells). `inline=0` (save to disk), `name`, and `size` are not supported.

Images occupy terminal cells, scroll with the text, and are freed once they
scroll out of the transcript. Images wider than the terminal are cropped to
the terminal width. Decoded bitmap size is capped at roughly 100 MB of pixel
data (150 MB on Android 15+, or the device's reported texture allocation
limit). There is no option to disable graphics support. The kitty graphics
protocol is not implemented.

### block glyph rendering

The renderer draws Unicode "legacy computing" mosaic glyphs geometrically
instead of relying on the font, so TUI tools that use them (e.g. `chafa`,
notcurses-based programs, terminal plotters) work even though almost no
Android monospace font covers them, and adjacent glyphs join without hairline
gaps. Covered ranges include block octants (U+1CD00–U+1CDE5, U+1FBE6/U+1FBE7),
sextants and separated sextants, one-eighth blocks, upper/right fractional
blocks, and quarter-block partials. Standard box-drawing (U+2500–U+257F) and
block elements (U+2580–U+259F) still come from the font. Consecutive
same-color glyphs are batched into single draw calls. This is always on.

### font variants

In addition to upstream's `~/.termux/font.ttf`, the app loads
`~/.termux/font-bold.ttf`, `~/.termux/font-italic.ttf`, and
`~/.termux/font-bold-italic.ttf` when present, and uses the real variant
glyphs for bold/italic/bold-italic terminal text. Any variant file that is
missing (or fails to load) falls back to upstream behavior for that style:
synthesized bold (fake-bold smearing) and/or synthesized italic (skewed
regular glyphs). If only some variants are provided, the closest one is used —
e.g. bold-italic text with only `font-bold.ttf` installed renders as the bold
face skewed. Cell metrics are always taken from the regular face, so variants
should be metrically compatible with it (faces from the same family, as with
Nerd Font distributions, are fine). Reload with `termux-reload-settings`.

### extra-keys toolbar

- **Multiple key panels**: the toolbar can hold any number of key panels, which
  you swipe between. See [extra-keys panels](#extra-keys-panels) below.
- **Double-tap modifier lock**: quickly tapping `CTRL`, `ALT`, `SHIFT`, or
  `FN` twice locks it (same effect as the existing long-press lock) so it
  stays applied across keystrokes until tapped again. Locked modifiers are
  outlined with a thin border.
- **Swipe-up cancellation**: pressing a key and sliding the finger up off the
  button before releasing cancels the key press (or opens the key's popup, if
  it defines one) instead of firing the key.
- **Scroll-lock icon**: the `SCROLL` key (which toggles auto-scroll-to-bottom,
  an upstream feature) now shows an icon instead of text — outlined when
  auto-scroll is on, filled when scroll lock is engaged.
- **Horizontal scroll reporting**: when scroll lock is engaged and the
  foreground app has mouse tracking active, horizontal finger drags are
  reported to the app as wheel-left/wheel-right events (buttons 66/67).
  Physical mouse horizontal wheels are not translated.

### extra-keys panels

Each page of the toolbar is a *panel*. Upstream has two — the extra keys and
the text input view — and you swipe between them. This fork lets you define as
many key panels as you like, in any order, with the text input view placed
wherever you want it.

Panels are declared with a json object whose entries are the panels, in the
order they are shown. The entry name is a descriptive name of your choosing and
has nothing to do with how the panel behaves:

```json
{
  "$schema": "https://termux.com/schemas/extra-keys-v2.schema.json",
  "function": {
    "keys": [["F1", "F2", "F3", "F4", "F5", "F6"],
             ["F7", "F8", "F9", "F10", "F11", "F12"]]
  },
  "main": {
    "default": true,
    "keys": [["ESC", "TAB", "CTRL", "ALT", "UP", "DOWN"],
             ["LEFT", "RIGHT", "HOME", "END", "PGUP", "PGDN"]]
  },
  "text_input": {
    "preset": "text_input_view"
  }
}
```

A panel entry sets exactly one of:

- **`keys`** — the rows of keys of the panel, in the same syntax `extra-keys`
  has always used (key names, `{key: …, popup: …}` objects, `macro`, `display`).
- **`preset`** — the name of a built-in panel. `text_input_view` is currently
  the only one and pulls in the toolbar text input view. Placing it yourself
  suppresses the automatic one; at most one panel may use it.

A panel may also set **`default`** to `true` to be the panel shown when the
toolbar is built. Without any panel marked, the middle panel is used — for an
even number of panels, the one before the middle. Panel selection is not
per-session: it is one current panel for the whole activity, as upstream.

If no panel declares `text_input_view`, one is appended as the last panel, so a
layout of key panels alone still gets the text input view where upstream puts
it. The toolbar is as tall as the tallest panel, and every panel shares
`extra-keys-style` and modifier state — a `CTRL` tapped on one panel applies to
a key tapped on another.

The json is parsed with the lax rules of org.json, the same parser
`extra-keys` has always used, so `//`, `#` and `/* */` comments, unquoted names
and single-quoted strings are all accepted. `$schema` is informational and is
not parsed; [`docs/extra-keys-v2.schema.json`](docs/extra-keys-v2.schema.json)
is the matching schema, for editor completion and validation.

Two properties feed the toolbar:

- **`extra-keys-json-file`**: the path of a json file holding the layout. A
  path that does not start with `/` is resolved against `~/.termux`, and a
  leading `~/` against the Termux home directory. It takes priority over
  `extra-keys`; if the file is missing or unreadable, a toast is shown and
  `extra-keys` is used instead.
- **`extra-keys`**: as upstream, but it now also accepts a panels object. A
  file is much more comfortable for anything past a couple of panels, since
  termux.properties needs every line but the last continued with `\`.

Either source still accepts the classic top-level array of rows, which defines
a single key panel followed by the text input view — exactly upstream's
behavior. The two formats are told apart by the top-level json value: an array
is the classic format, an object is panels.

Both are re-read by `termux-reload-settings`, which rebuilds the panels and
returns to the default one.

### toolbar text input history

The toolbar's text input field keeps the last 20 submitted entries for the
lifetime of the activity (not persisted across restarts). Fling up on the
field to recall older entries, fling down to return toward newer ones; an
in-progress unsubmitted edit is preserved and restored when you swipe back
past the newest entry. Consecutive duplicates and blank entries are skipped.

### zoom and font size

- **Zoom Per Session** (Settings → Termux → Terminal View, default off): when
  enabled, pinch-zoom and `Ctrl+Alt` +/- change the font size of only the
  current session; each session keeps its own zoom level, and new sessions
  inherit the size of the session that was current when they were created.
  Per-session sizes are in-memory only — after an app restart, sessions start
  from the global font size again. When off, zoom changes the single global
  font size as upstream does.
- **`zoom-minimum-dp`** (integer property, `1`–`64`, default `4`): the
  smallest font size, in dp, that zooming out can reach. Upstream hardcodes
  4 dp; raise it to prevent accidentally zooming text into illegibility.

### clipboard, paste, and text selection

- **Clipboard image paste** (`clipboard-image-paste`, boolean, default
  `false`): when enabled and the clipboard holds an image, any paste action
  (selection-menu Paste, the `PASTE` extra key, `Ctrl+Alt+V`, middle click)
  writes the image to a file and pastes the file's absolute path instead of
  text. Files are named `clipboard_yyyyMMdd_HHmmss.<ext>` (extension from the
  image MIME type, defaulting to `png`) and written to
  `clipboard-image-paste-dir` (default `$PREFIX/tmp`; falls back to the
  default if the configured directory is not writable).
- **Paste with Newlines**: a new terminal context-menu (long-press) action
  that normalizes all line endings in the pasted text to `\n` instead of the
  regular paste's conversion to `\r` (Enter). Useful when a program treats
  `\r` as "execute" but `\n` as a literal line break. Bracketed paste mode is
  honored as usual. This action does not do the clipboard image check.
- **Open URL**: when the text selection is exactly one URL, the selection
  toolbar offers an "Open URL" action that launches it in the associated
  Android app (or the app chooser).

### terminal brightness

The `brightness` property (float, `0`–`10`, default `1`) multiplies the RGB
channels of everything the terminal draws — text, cursor, non-default cell
backgrounds, and inline images — while leaving the default background color
untouched. Values below 1 dim the terminal; values above 1 brighten it.
Out-of-range or malformed values fall back to the default. Applied live on
`termux-reload-settings`.

### terminal identification (XTVERSION)

The emulator answers the XTVERSION query (`CSI > 0 q`, also with the
parameter omitted) with `DCS > | Termux(<app version>) ST`. The reported
product name can be changed with the `terminal-product-name` property
(default `Termux`); control characters are stripped from the value, and the
app version is included when available in the session environment.

### smaller changes

- OSC 52 clipboard writes accept up to 100 KB of payload (upstream limit was
  8 KB), matching Android's clipboard transaction limit; oversized sequences
  are discarded without setting the clipboard.
- The `disable-session-title-change-toast` property (boolean, default
  `false`) suppresses the toast shown when a background session changes its
  terminal title.
- Cleared full-width lines no longer keep a stale line-wrap flag, fixing
  text selection and copy treating them as wrapped continuations.
- Very wide terminal rows no longer overflow an internal 16-bit index.
- OSC/DCS parsing has a fast path and larger buffers, so large escape
  sequences (images, big pastes) are handled without corrupting the screen.
- Sixel streams whose optional DCS parameters are partially omitted are
  accepted.

## building

Same as upstream: JDK 17, Gradle wrapper (Gradle 9.2, AGP 8.13), Android SDK
platform 36, and NDK 29.x. Build a debug APK with:

```sh
./gradlew assembleDebug
```

APKs are written to `app/build/outputs/apk/debug/`.

## license

Unchanged from upstream: the repository is released under
[GPLv3 only](LICENSE.md), with the `terminal-emulator` and `terminal-view`
components under Apache 2.0 (inherited from Terminal Emulator for Android)
and additional exceptions listed in
[`termux-shared/LICENSE.md`](termux-shared/LICENSE.md).
