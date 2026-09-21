/*
 * Glyph repertoire and selected geometry adapted from kitty/decorations.c,
 * Copyright (C) 2024 Kovid Goyal. Distributed under GPL-3.0-only.
 * See terminal-view/NOTICE.md for provenance and license details.
 */
package com.termux.view;

import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Font-independent cell artwork. Rectangles and straight lines never acquire antialiased seams. */
final class TerminalGlyphs {
    private TerminalGlyphs() {}

    static boolean isSupported(int cp) {
        return between(cp, 0x2500, 0x259F) || cp == 0x25C9 || cp == 0x25CB || cp == 0x25CF
            || between(cp, 0x25D6, 0x25D7) || between(cp, 0x25DC, 0x25E5)
            || between(cp, 0x2800, 0x28FF) || between(cp, 0xE0B0, 0xE0BF)
            || between(cp, 0xE0D6, 0xE0D7) || between(cp, 0xEE00, 0xEE0B)
            || between(cp, 0xF5D0, 0xF60D) || between(cp, 0x1FB00, 0x1FBAE)
            || between(cp, 0x1FBCE, 0x1FBEF) || between(cp, 0x1CD00, 0x1CDE5)
            || between(cp, 0x1CC1B, 0x1CC3F) || between(cp, 0x1CE16, 0x1CE19)
            || between(cp, 0x1CE51, 0x1CEAF);
    }

    private static boolean between(int cp, int start, int end) {
        return cp >= start && cp <= end;
    }

    static final class Geometry {
        final Path pixels = new Path();
        final Path smooth = new Path();
    }

    static Geometry create(int codePoint, int width, int height) {
        if (!isSupported(codePoint) || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid terminal glyph or cell dimensions");
        Builder b = new Builder(width, height);
        int spec = BlockGlyphs.getSpec(codePoint);
        if (spec != BlockGlyphs.UNSUPPORTED) b.blocks(spec);
        else b.draw(codePoint);
        // Stroke caps and curves may extend outside the cell. Never paint a neighbor's background.
        Path bounds = new Path();
        bounds.addRect(0, 0, width, height, Path.Direction.CW);
        b.geometry.smooth.op(bounds, Path.Op.INTERSECT);
        return b.geometry;
    }

    private static final class Builder {
        final Geometry geometry = new Geometry();
        final int w, h, unit, cx, cy;
        final Paint stroke = new Paint();

        Builder(int width, int height) {
            w = width;
            h = height;
            cx = w / 2;
            cy = h / 2;
            // Fractional zoom can alternate cell widths by one pixel. Derive stroke weight
            // from the shared line height so adjoining cells still have identical strokes.
            unit = Math.max(1, Math.round(h / 16f));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            stroke.setStrokeJoin(Paint.Join.ROUND);
        }

        void rect(int left, int top, int right, int bottom) {
            left = Math.max(0, left);
            top = Math.max(0, top);
            right = Math.min(w, right);
            bottom = Math.min(h, bottom);
            if (right > left && bottom > top)
                geometry.pixels.addRect(left, top, right, bottom, Path.Direction.CW);
        }

        void blocks(int spec) {
            int cols = BlockGlyphs.getColumns(spec), rows = BlockGlyphs.getRows(spec);
            int mask = BlockGlyphs.getMask(spec);
            boolean separated = BlockGlyphs.isSeparated(spec);
            int gapX = separated ? Math.max(1, w / 8) : 0;
            int gapY = separated ? Math.max(1, h / 8) : 0;
            int blockW = Math.max(0, (w - (cols + 1) * gapX) / cols);
            int blockH = Math.max(0, (h - (rows + 1) * gapY) / rows);
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if ((mask & (1 << (y * cols + x))) == 0) continue;
                    if (separated) {
                        int left = gapX + x * (gapX + blockW), top = gapY + y * (gapY + blockH);
                        rect(left, top, left + blockW, top + blockH);
                    } else {
                        rect(x * w / cols, y * h / rows, (x + 1) * w / cols, (y + 1) * h / rows);
                    }
                }
            }
        }

        void smooth(Path path) {
            geometry.smooth.op(path, Path.Op.UNION);
        }

        void stroke(Path path, float thickness) {
            stroke.setStrokeWidth(thickness);
            Path fill = new Path();
            stroke.getFillPath(path, fill);
            smooth(fill);
        }

        void line(float x1, float y1, float x2, float y2) {
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            stroke(path, unit);
        }

        Path polygon(float... points) {
            Path path = new Path();
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
            path.close();
            return path;
        }

        void arc(float x, float y, float rx, float ry, float start, float sweep) {
            Path path = new Path();
            path.addArc(new RectF(x - rx, y - ry, x + rx, y + ry), start, sweep);
            stroke(path, unit);
        }

        Path disk(float x, float y, float radius) {
            Path path = new Path();
            path.addCircle(x, y, Math.max(0.5f, radius), Path.Direction.CW);
            return path;
        }

        void hline(int left, int right, int y, int weight) {
            int size = weight == 2 ? unit * 2 : unit;
            int top = y - size / 2;
            rect(left, top, right, top + size);
        }

        void vline(int top, int bottom, int x, int weight) {
            int size = weight == 2 ? unit * 2 : unit;
            int left = x - size / 2;
            rect(left, top, left + size, bottom);
        }

        void box(int cp) {
            if (cp >= 0x256D && cp <= 0x2570) {
                roundedCorner(cp == 0x256D || cp == 0x2570, cp <= 0x256E);
                return;
            }
            if (cp >= 0x2571 && cp <= 0x2573) {
                if (cp != 0x2572) line(0, h, w, 0);
                if (cp != 0x2571) line(0, 0, w, h);
                return;
            }
            if (between(cp, 0x2504, 0x250B) || between(cp, 0x254C, 0x254F)) {
                int count = cp >= 0x254C ? 2 : cp >= 0x2508 ? 4 : 3;
                boolean vertical = (cp & 2) != 0;
                int size = vertical ? h : w;
                int gap = Math.max(1, size / (count * 4));
                for (int i = 0; i < count; i++) {
                    int start = i * size / count + gap / 2;
                    int end = (i + 1) * size / count - (gap - gap / 2);
                    if (vertical) vline(start, end, cx, 1 + (cp & 1));
                    else hline(start, end, cy, 1 + (cp & 1));
                }
                return;
            }
            int arms = GlyphMappings.BOX_ARMS[cp - 0x2500];
            for (int direction = 0; direction < 4; direction++) {
                int weight = (arms >> (direction * 2)) & 3;
                if (weight == 0) continue;
                if (weight == 3) doubleArm(direction, arms);
                else {
                    // Extend only far enough to meet the perpendicular stroke. Extending every
                    // arm by a fixed amount puts protruding stubs on corners and half-lines.
                    int perpendicular = (direction & 1) == 0
                        ? Math.max((arms >> 2) & 3, (arms >> 6) & 3)
                        : Math.max(arms & 3, (arms >> 4) & 3);
                    int before = perpendicular * unit / 2;
                    int after = (perpendicular * unit + 1) / 2;
                    switch (direction) {
                        case 0: vline(0, cy + after, cx, weight); break;
                        case 1: hline(cx - before, w, cy, weight); break;
                        case 2: vline(cy - before, h, cx, weight); break;
                        case 3: hline(0, cx + after, cy, weight); break;
                    }
                }
            }
        }

        void doubleArm(int dir, int arms) {
            boolean horizontal = (dir & 1) != 0;
            boolean positive = dir == 1 || dir == 2;
            int before = horizontal ? arms & 3 : (arms >> 6) & 3;
            int after = horizontal ? (arms >> 4) & 3 : (arms >> 2) & 3;
            for (int side = -1; side <= 1; side += 2) {
                int join = 0;
                if (before == 3 && after == 3) join = positive ? -unit : unit;
                else if (before == 3) join = (positive ? -side : side) * unit;
                else if (after == 3) join = (positive ? side : -side) * unit;
                int center = horizontal ? cx : cy;
                int start = positive ? center + join - unit / 2 : 0;
                int end = positive ? (horizontal ? w : h) : center + join + (unit + 1) / 2;
                if (horizontal) hline(start, end, cy + side * unit, 1);
                else vline(start, end, cx + side * unit, 1);
            }
        }

        void roundedCorner(boolean right, boolean down) {
            // Straight endpoints stay on the same pixel grid as ordinary box lines.
            float x = cx - unit / 2 + unit / 2f;
            float y = cy - unit / 2 + unit / 2f;
            float radius = Math.min(Math.min(x, w - x), Math.min(y, h - y));
            float dx = right ? 1 : -1, dy = down ? 1 : -1;
            Path path = new Path();
            path.moveTo(right ? w : 0, y);
            path.lineTo(x + dx * radius, y);
            path.quadTo(x, y, x, y + dy * radius);
            path.lineTo(x, down ? h : 0);
            stroke(path, unit);
        }

        void braille(int mask) {
            int dotW = Math.max(1, w / 4), dotH = Math.max(1, h / 8);
            for (int i = 0; i < 8; i++) {
                if ((mask & (1 << i)) == 0) continue;
                int col = i < 3 || i == 6 ? 0 : 1;
                int row = i >= 6 ? 3 : i % 3;
                int left = (2 * col + 1) * w / 4 - dotW / 2;
                int top = (2 * row + 1) * h / 8 - dotH / 2;
                rect(left, top, left + dotW, top + dotH);
            }
        }

        void shade(int cp) {
            int cols = cp == 0x1FB97 ? 1 : between(cp, 0x1FB95, 0x1FB96) ? 4 : 12;
            int squareW = Math.max(1, w / cols);
            int squareH = between(cp, 0x1FB95, 0x1FB97) ? Math.max(1, h / 4) : squareW;
            boolean inverse = cp == 0x2593 || between(cp, 0x1FB90, 0x1FB94) || cp == 0x1FB96 || cp == 0x1FB97;
            boolean light = cp == 0x2591 || cp == 0x2593;
            int left = 0, right = w, top = 0, bottom = h;
            switch (cp) {
                case 0x1FB8C: case 0x1FB94: right = cx; break;
                case 0x1FB8D: case 0x1FB93: left = cx; break;
                case 0x1FB8E: case 0x1FB92: bottom = cy; break;
                case 0x1FB8F: case 0x1FB91: top = cy; break;
            }
            for (int y = 0; y < h; y += squareH) {
                for (int x = 0; x < w; x += squareW) {
                    boolean fill = light ? ((x / squareW) & 1) == 0 && ((y / squareH) & 1) == 0
                        : ((x / squareW + y / squareH) & 1) == 0;
                    if (fill != inverse)
                        rect(Math.max(x, left), Math.max(y, top), Math.min(x + squareW, right), Math.min(y + squareH, bottom));
                }
            }
            switch (cp) {
                case 0x1FB91: rect(0, 0, w, cy); break;
                case 0x1FB92: rect(0, cy, w, h); break;
                case 0x1FB93: rect(0, 0, cx, h); break;
                case 0x1FB94: rect(cx, 0, w, h); break;
            }
        }

        Path triangle(int side) {
            switch (side) {
                case 0: return polygon(0, 0, w, 0, w / 2f, h / 2f); // upper
                case 1: return polygon(w, 0, w, h, w / 2f, h / 2f); // right
                case 2: return polygon(0, h, w, h, w / 2f, h / 2f); // lower
                default: return polygon(0, 0, 0, h, w / 2f, h / 2f); // left
            }
        }

        Path cornerTriangle(int corner) {
            switch (corner) {
                case 0: return polygon(0, 0, w, 0, 0, h); // upper left
                case 1: return polygon(0, 0, w, 0, w, h); // upper right
                case 2: return polygon(w, 0, w, h, 0, h); // lower right
                default: return polygon(0, 0, 0, h, w, h); // lower left
            }
        }

        void mosaic(int index) {
            float[] m = GlyphMappings.MOSAICS[index];
            float ax = m[1] * w, ay = m[2] * h, bx = m[3] * w, by = m[4] * h;
            // Clip the cell rectangle against the line's half-plane.
            float[] xs = {0, w, w, 0}, ys = {0, 0, h, h};
            Path path = new Path();
            boolean started = false;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                float da = (ys[i] - ay) - (by - ay) * (xs[i] - ax) / (bx - ax);
                float db = (ys[j] - ay) - (by - ay) * (xs[j] - ax) / (bx - ax);
                boolean inside = m[0] == 1 ? da >= 0 : da <= 0;
                boolean nextInside = m[0] == 1 ? db >= 0 : db <= 0;
                if (inside) {
                    if (!started) { path.moveTo(xs[i], ys[i]); started = true; }
                    else path.lineTo(xs[i], ys[i]);
                }
                if (inside != nextInside) {
                    float t = da / (da - db), x = xs[i] + t * (xs[j] - xs[i]), y = ys[i] + t * (ys[j] - ys[i]);
                    if (!started) { path.moveTo(x, y); started = true; }
                    else path.lineTo(x, y);
                }
            }
            path.close();
            smooth(path);
        }

        void powerline(int cp) {
            switch (cp) {
                case 0xE0B0: smooth(polygon(0, 0, w, h / 2f, 0, h)); break;
                case 0xE0B2: smooth(polygon(w, 0, 0, h / 2f, w, h)); break;
                case 0xE0D7: {
                    Path p = polygon(0, 0, w, 0, w, h, 0, h);
                    p.op(polygon(0, 0, w, h / 2f, 0, h), Path.Op.DIFFERENCE);
                    smooth(p); break;
                }
                case 0xE0D6: {
                    Path p = polygon(0, 0, w, 0, w, h, 0, h);
                    p.op(polygon(w, 0, 0, h / 2f, w, h), Path.Op.DIFFERENCE);
                    smooth(p); break;
                }
                case 0xE0B1: line(0, 0, w, h / 2f); line(w, h / 2f, 0, h); break;
                case 0xE0B3: line(w, 0, 0, h / 2f); line(0, h / 2f, w, h); break;
                case 0xE0B4: case 0xE0B6: case 0xE0B5: case 0xE0B7: {
                    boolean right = cp == 0xE0B4 || cp == 0xE0B5;
                    float x = right ? 0 : w;
                    if ((cp & 1) != 0) arc(x, h / 2f, w - unit / 2f, h / 2f - unit / 2f, right ? -90 : 90, 180);
                    else {
                        Path p = new Path();
                        p.addOval(new RectF(x - w, 0, x + w, h), Path.Direction.CW);
                        smooth(p);
                    }
                    break;
                }
                case 0xE0B8: smooth(cornerTriangle(3)); break;
                case 0xE0BA: smooth(cornerTriangle(2)); break;
                case 0xE0BC: smooth(cornerTriangle(0)); break;
                case 0xE0BE: smooth(cornerTriangle(1)); break;
                case 0xE0B9: case 0xE0BF: line(0, 0, w, h); break;
                case 0xE0BB: case 0xE0BD: line(0, h, w, 0); break;
                default: throw new IllegalArgumentException("Unknown Powerline glyph");
            }
        }

        void progress(int cp) {
            int i = cp - 0xEE00;
            if (i >= 6) {
                int[] starts = {235, 270, 315, 360, 80, 170};
                int[] sweeps = {70, 120, 155, 180, 140, 100};
                float r = Math.max(0.5f, (Math.min(w, h) - unit) / 2f);
                arc(w / 2f, h / 2f, r, r, starts[i - 6], sweeps[i - 6]);
                return;
            }
            rect(0, 0, w, unit);
            rect(0, h - unit, w, h);
            if (i % 3 == 0) rect(0, 0, unit, h);
            if (i % 3 == 2) rect(w - unit, 0, w, h);
            if (i >= 3) rect(i % 3 == 0 ? 3 * unit : 0, 3 * unit, i % 3 == 2 ? w - 3 * unit : w, h - 3 * unit);
        }

        void branch(int cp) {
            int i = cp - 0xF5D0;
            if (i == 0) { hline(0, w, cy, 1); return; }
            if (i == 1) { vline(0, h, cx, 1); return; }
            if (i < 6) {
                boolean vertical = i >= 4, reverse = i == 3 || i == 5;
                int size = vertical ? h : w;
                int count = vertical ? 5 : 4;
                for (int n = 0; n < count; n++) {
                    int start = n * size / count;
                    int length = Math.max(1, (count - n) * size / (count * count));
                    int end = Math.min(size, start + length);
                    if (reverse) { int old = start; start = size - end; end = size - old; }
                    if (vertical) vline(start, end, cx, 1); else hline(start, end, cy, 1);
                }
                return;
            }
            if (i < 30) {
                // Corner bits: top-left, top-right, bottom-left, bottom-right of a box.
                int[] corners = {1, 2, 4, 8, 4, 1, 5, 8, 2, 10, 2, 1, 3, 8, 4, 12, 12, 3, 10, 5, 9, 6, 9, 6};
                int[] straights = {0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 2, 2, 0, 2, 2, 0, 1, 1, 2, 2, 1, 1, 2, 2};
                int mask = corners[i - 6];
                if ((mask & 1) != 0) roundedCorner(true, true);
                if ((mask & 2) != 0) roundedCorner(false, true);
                if ((mask & 4) != 0) roundedCorner(true, false);
                if ((mask & 8) != 0) roundedCorner(false, false);
                if (straights[i - 6] == 1) vline(0, h, cx, 1);
                if (straights[i - 6] == 2) hline(0, w, cy, 1);
                return;
            }
            // Commit nodes: up/right/down/left bits, solid then hollow for each connection set.
            int[] edges = {0, 2, 8, 10, 4, 1, 5, 6, 12, 3, 9, 7, 13, 14, 11, 15};
            int mask = edges[(i - 30) / 2];
            float r = Math.max(1f, Math.min(w, h) * 0.4f);
            if ((mask & 1) != 0) vline(0, cy, cx, 1);
            if ((mask & 2) != 0) hline(cx, w, cy, 1);
            if ((mask & 4) != 0) vline(cy, h, cx, 1);
            if ((mask & 8) != 0) hline(0, cx, cy, 1);
            Path circle = disk(cx, cy, r);
            if ((i & 1) != 0) {
                Path hole = disk(cx, cy, Math.max(0.5f, r - unit));
                circle.op(hole, Path.Op.DIFFERENCE);
                geometry.pixels.op(hole, Path.Op.DIFFERENCE);
            }
            smooth(circle);
        }

        void legacyDiagonal(int cp) {
            if (cp <= 0x1FBAE) {
                int[] masks = {1, 2, 4, 8, 5, 10, 12, 3, 9, 6, 14, 13, 11, 7, 15};
                int mask = masks[cp - 0x1FBA0];
                if ((mask & 1) != 0) line(0, cy, cx, 0);
                if ((mask & 2) != 0) line(w, cy, cx, 0);
                if ((mask & 4) != 0) line(0, cy, cx, h);
                if ((mask & 8) != 0) line(w, cy, cx, h);
                return;
            }
            float[][] points = {
                {1, .5f, 0, 1}, {1, 0, 0, .5f}, {0, 0, 1, .5f}, {0, .5f, 1, 1},
                {0, 0, .5f, 1}, {.5f, 0, 1, 1}, {1, 0, .5f, 1}, {.5f, 0, 0, 1},
                {0, 0, .5f, .5f, 1, 0}, {1, 0, .5f, .5f, 1, 1},
                {0, 1, .5f, .5f, 1, 1}, {0, 0, .5f, .5f, 0, 1},
                {0, 0, .5f, 1, 1, 0}, {1, 0, 0, .5f, 1, 1},
                {0, 1, .5f, 0, 1, 1}, {0, 0, 1, .5f, 0, 1}
            };
            float[] p = points[cp - 0x1FBD0];
            for (int i = 0; i < p.length - 2; i += 2) line(p[i] * w, p[i + 1] * h, p[i + 2] * w, p[i + 3] * h);
        }

        void circlePart(int cp) {
            if (between(cp, 0x1CC30, 0x1CC3F)) {
                int i = cp - 0x1CC30, col = i % 4, row = i / 4;
                boolean quarter = (col == 1 || col == 2) && (row == 1 || row == 2);
                float x = (2 - col) * w;
                float y = (2 - row) * h;
                float rx = (quarter ? w : 2 * w) - unit / 2f;
                float ry = (quarter ? h : 2 * h) - unit / 2f;
                int[] starts = {210, 240, 270, 300, 180, 180, 270, 330, 150, 90, 0, 0, 120, 90, 60, 30};
                arc(x, y, Math.max(.5f, rx), Math.max(.5f, ry), starts[i], quarter ? 90 : 30);
                return;
            }
            boolean outline = cp <= 0x1FBE3;
            int i = outline ? cp - 0x1FBE0 : cp - 0x1FBE8;
            float r = w / 2f, x, y;
            if (i < 4) {
                x = i == 1 ? w : i == 3 ? 0 : w / 2f;
                y = i == 0 ? 0 : i == 2 ? h : h / 2f;
            } else {
                x = i == 4 || i == 6 ? w : 0;
                y = i == 4 || i == 7 ? 0 : h;
            }
            if (outline) {
                if (i == 0) y += unit / 2f;
                if (i == 2) y -= unit / 2f;
                int[] starts = {0, 90, 180, 270};
                arc(x, y, Math.max(.5f, r - unit / 2f), Math.max(.5f, r - unit / 2f), starts[i], 180);
            } else smooth(disk(x, y, r));
        }

        void draw(int cp) {
            if (between(cp, 0x2500, 0x257F)) { box(cp); return; }
            if (between(cp, 0x2800, 0x28FF)) { braille(cp - 0x2800); return; }
            if (between(cp, 0xE0B0, 0xE0BF) || between(cp, 0xE0D6, 0xE0D7)) { powerline(cp); return; }
            if (between(cp, 0xEE00, 0xEE0B)) { progress(cp); return; }
            if (between(cp, 0xF5D0, 0xF60D)) { branch(cp); return; }
            if (between(cp, 0x1FB3C, 0x1FB67)) { mosaic(cp - 0x1FB3C); return; }
            if (between(cp, 0x1FB68, 0x1FB6F)) {
                int[] sides = {3, 0, 1, 2};
                Path p = triangle(sides[(cp - 0x1FB68) % 4]);
                if (cp < 0x1FB6C) {
                    Path all = polygon(0, 0, w, 0, w, h, 0, h);
                    all.op(p, Path.Op.DIFFERENCE);
                    p = all;
                }
                smooth(p); return;
            }
            if (between(cp, 0x2591, 0x2593) || between(cp, 0x1FB8C, 0x1FB97)) { shade(cp); return; }
            if (between(cp, 0x1FBA0, 0x1FBAE) || between(cp, 0x1FBD0, 0x1FBDF)) { legacyDiagonal(cp); return; }
            if (between(cp, 0x1CC30, 0x1CC3F) || between(cp, 0x1FBE0, 0x1FBE3) || between(cp, 0x1FBE8, 0x1FBEF)) { circlePart(cp); return; }
            switch (cp) {
                case 0x25CB: case 0x25C9: case 0x25CF: {
                    float r = Math.max(.5f, (Math.min(w, h) - unit) / 2f);
                    if (cp == 0x25CF) smooth(disk(w / 2f, h / 2f, r));
                    else {
                        arc(w / 2f, h / 2f, r, r, 0, 360);
                        if (cp == 0x25C9) smooth(disk(w / 2f, h / 2f, r / 2));
                    }
                    break;
                }
                case 0x25D6: powerline(0xE0B6); break;
                case 0x25D7: powerline(0xE0B4); break;
                case 0x25DC: case 0x25DD: case 0x25DE: case 0x25DF: case 0x25E0: case 0x25E1: {
                    int[] starts = {180, 270, 0, 90, 180, 0};
                    float r = Math.max(.5f, (Math.min(w, h) - unit) / 2f);
                    arc(w / 2f, h / 2f, r, r, starts[cp - 0x25DC], cp >= 0x25E0 ? 180 : 90);
                    break;
                }
                case 0x25E2: smooth(cornerTriangle(2)); break;
                case 0x25E3: smooth(cornerTriangle(3)); break;
                case 0x25E4: smooth(cornerTriangle(0)); break;
                case 0x25E5: smooth(cornerTriangle(1)); break;
                case 0x1FB7C: rect(0, 0, w / 8, h); rect(0, 7 * h / 8, w, h); break;
                case 0x1FB7D: rect(0, 0, w / 8, h); rect(0, 0, w, h / 8); break;
                case 0x1FB7E: rect(7 * w / 8, 0, w, h); rect(0, 0, w, h / 8); break;
                case 0x1FB7F: rect(7 * w / 8, 0, w, h); rect(0, 7 * h / 8, w, h); break;
                case 0x1FB80: rect(0, 0, w, h / 8); rect(0, 7 * h / 8, w, h); break;
                case 0x1FB81:
                    for (int row : new int[]{0, 2, 4, 7}) rect(0, row * h / 8, w, (row + 1) * h / 8);
                    break;
                case 0x1FB98: case 0x1FB99:
                    for (int x = -w; x < w * 2; x += Math.max(2, w / 4)) {
                        if (cp == 0x1FB98) line(x, 0, x + w, h); else line(x, h, x + w, 0);
                    }
                    break;
                case 0x1FB9A: smooth(triangle(0)); smooth(triangle(2)); break;
                case 0x1FB9B: smooth(triangle(1)); smooth(triangle(3)); break;
                case 0x1FB9C: case 0x1FB9D: case 0x1FB9E: case 0x1FB9F: {
                    shade(0x2592);
                    Path p = new Path(geometry.pixels);
                    geometry.pixels.reset();
                    p.op(cornerTriangle(cp - 0x1FB9C), Path.Op.INTERSECT);
                    smooth(p); break;
                }
                case 0x1CC1B: case 0x1CC1C:
                    hline(0, w, cy, 1);
                    vline(cp == 0x1CC1B ? 0 : cy, cp == 0x1CC1B ? cy + unit : h, 3 * w / 4, 1);
                    break;
                case 0x1CC1D: vline(0, h / 4 + unit, cx, 1); hline(0, cx + unit, h / 4, 1); break;
                case 0x1CC1E: vline(3 * h / 4, h, cx, 1); hline(0, cx + unit, 3 * h / 4, 1); break;
                case 0x1CC1F: case 0x1CC20:
                    for (int shift : new int[]{-unit, unit}) {
                        if (cp == 0x1CC1F) line(shift, h, w + shift, 0);
                        else line(shift, 0, w + shift, h);
                    }
                    break;
                case 0x1CE16: case 0x1CE17: case 0x1CE18: case 0x1CE19:
                    vline(0, h, cx, 1);
                    hline(cp < 0x1CE18 ? cx : 0, cp < 0x1CE18 ? w : cx + unit, (cp & 1) == 0 ? h / 4 : 3 * h / 4, 1);
                    break;
                default: throw new IllegalArgumentException("Missing terminal glyph U+" + Integer.toHexString(cp));
            }
        }
    }
}
