package com.termux.view;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerminalGlyphsRenderingTest {
    // Independent inventory from kitty fonts.c at 31c16b37b. Keep range boundaries explicit.
    private static final int[][] RANGES = {
        {0x2500, 0x259F}, {0x25C9, 0x25C9}, {0x25CB, 0x25CB}, {0x25CF, 0x25CF},
        {0x25D6, 0x25D7}, {0x25DC, 0x25E5}, {0x2800, 0x28FF}, {0xE0B0, 0xE0BF},
        {0xE0D6, 0xE0D7}, {0xEE00, 0xEE0B}, {0xF5D0, 0xF60D}, {0x1FB00, 0x1FBAE},
        {0x1FBCE, 0x1FBEF}, {0x1CD00, 0x1CDE5}, {0x1CC1B, 0x1CC3F},
        {0x1CE16, 0x1CE19}, {0x1CE51, 0x1CEAF}
    };

    private static List<Integer> repertoire() {
        List<Integer> result = new ArrayList<>();
        for (int[] range : RANGES) for (int cp = range[0]; cp <= range[1]; cp++) result.add(cp);
        return result;
    }

    private static void drawGeometry(Canvas canvas, int cp, int width, int height, Paint paint) {
        TerminalGlyphs.Geometry geometry = TerminalGlyphs.create(cp, width, height);
        paint.setAntiAlias(false);
        canvas.drawPath(geometry.pixels, paint);
        paint.setAntiAlias(true);
        canvas.drawPath(geometry.smooth, paint);
    }

    private static Bitmap glyph(int cp, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width + 8, height + 8, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(4, 4);
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        drawGeometry(canvas, cp, width, height, paint);
        return bitmap;
    }

    @Test
    public void completeRepertoireRendersInsideItsCellAtSmallAndOddSizes() {
        List<Integer> chars = repertoire();
        assertEquals(1098, chars.size());
        for (int cp : chars) {
            assertTrue("U+" + Integer.toHexString(cp), TerminalGlyphs.isSupported(cp));
            for (int[] size : new int[][]{{1, 1}, {3, 5}, {7, 13}, {16, 32}, {17, 33}}) {
                Bitmap bitmap = glyph(cp, size[0], size[1]);
                int filled = 0;
                for (int y = 0; y < bitmap.getHeight(); y++) {
                    for (int x = 0; x < bitmap.getWidth(); x++) {
                        int alpha = Color.alpha(bitmap.getPixel(x, y));
                        if (x < 4 || y < 4 || x >= size[0] + 4 || y >= size[1] + 4)
                            assertEquals("Glyph escaped cell U+" + Integer.toHexString(cp), 0, alpha);
                        filled += alpha;
                    }
                }
                if (size[0] >= 16 && cp != 0x2800)
                    assertTrue("Blank glyph U+" + Integer.toHexString(cp), filled > 0);
                if (cp == 0x2800) assertEquals(0, filled);
                bitmap.recycle();
            }
        }
        for (int cp : new int[]{'A', 0x24FF, 0x25A0, 0x25CA, 0x1CDE6, 0x1FBAF, 0x1FBCD, 0x1FBF0})
            assertFalse(TerminalGlyphs.isSupported(cp));
    }

    @Test
    public void complementaryBlocksTileOddCellsWithoutTransparentPixels() {
        int[][] pairs = {{0x2580, 0x2584}, {0x258C, 0x2590}, {0x259A, 0x259E},
            {0x2581, 0x1FB86}, {0x258F, 0x1FB8B}};
        for (int[] size : new int[][]{{7, 13}, {16, 32}, {17, 33}}) {
            for (int[] pair : pairs) {
                Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
                Paint paint = new Paint();
                paint.setColor(Color.WHITE);
                Canvas canvas = new Canvas(bitmap);
                for (int cp : pair) drawGeometry(canvas, cp, size[0], size[1], paint);
                for (int y = 0; y < size[1]; y++) for (int x = 0; x < size[0]; x++)
                    assertEquals("Gap in complementary blocks", Color.WHITE, bitmap.getPixel(x, y));
            }
        }
    }

    @Test
    public void separatedBlocksHaveIntentionalGapsAndShadesHaveOrderedCoverage() {
        Bitmap separated = glyph(0x1CC2F, 24, 40);
        assertEquals(0, separated.getPixel(4, 4));
        assertEquals(Color.WHITE, separated.getPixel(8, 11));
        assertEquals(0, separated.getPixel(16, 24));
        int previous = 0;
        for (int cp = 0x2591; cp <= 0x2593; cp++) {
            Bitmap bitmap = glyph(cp, 24, 40);
            int area = 0;
            for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
                area += Color.alpha(bitmap.getPixel(x, y));
            assertTrue(area > previous);
            previous = area;
        }
    }

    private static TerminalEmulator emulator(int columns, int rows, String text) {
        TerminalOutput output = new TerminalOutput() {
            public void write(byte[] data, int offset, int count) {}
            public void titleChanged(String oldTitle, String newTitle) {}
            public void onCopyTextToClipboard(String text) {}
            public void onPasteTextFromClipboard() {}
            public void onBell() {}
            public void onColorsChanged() {}
        };
        TerminalEmulator emulator = new TerminalEmulator(output, columns, rows, 10, 20, 100, null);
        byte[] bytes = ("\033[?25l" + text).getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
        return emulator;
    }

    private static Bitmap render(TerminalRenderer renderer, TerminalEmulator emulator, boolean selected) {
        Bitmap bitmap = Bitmap.createBitmap(Math.round(emulator.mColumns * renderer.mFontWidth) + 4,
            renderer.mFontLineSpacingAndAscent + emulator.mRows * renderer.mFontLineSpacing + 4, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLACK);
        renderer.render(emulator, new Canvas(bitmap), 0, selected ? 0 : -1, selected ? 0 : -1, 0, emulator.mColumns);
        return bitmap;
    }

    @Test
    public void adjacentMulticolorBlocksHaveNoSeamsAtFractionalZoom() {
        int[] colors = {0xFFFF3311, 0xFF11DD88, 0xFF2244FF};
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < 3; row++) {
            if (row > 0) text.append("\r\n");
            for (int col = 0; col < 24; col++) {
                int color = colors[(row + col) % colors.length];
                text.append("\033[38;2;").append(Color.red(color)).append(';').append(Color.green(color))
                    .append(';').append(Color.blue(color)).append("m█");
            }
        }
        for (float size : new float[]{10.5f, 13.25f, 20.25f, 31.5f, 64.25f}) {
            TerminalRenderer renderer = new TerminalRenderer(size, Typeface.MONOSPACE, 1f);
            Bitmap bitmap = render(renderer, emulator(24, 4, text.toString()), false);
            for (int row = 0; row < 3; row++) {
                int top = renderer.mFontLineSpacingAndAscent + row * renderer.mFontLineSpacing;
                for (int col = 0; col < 24; col++) {
                    int left = Math.round(col * renderer.mFontWidth), right = Math.round((col + 1) * renderer.mFontWidth);
                    for (int y = top; y < top + renderer.mFontLineSpacing; y++) for (int x = left; x < right; x++)
                        assertEquals("Seam at size " + size + ", " + x + "," + y,
                            colors[(row + col) % colors.length], bitmap.getPixel(x, y));
                }
            }
        }
    }

    @Test
    public void foregroundBackgroundStylesAndSelectionRemainCorrect() {
        TerminalRenderer renderer = new TerminalRenderer(20.25f, Typeface.MONOSPACE, .5f);
        TerminalEmulator emulator = emulator(8, 4, "\033[38;2;200;100;40;48;2;40;80;160m▀▄\033[1;3m▀\033[0m");
        Bitmap bitmap = render(renderer, emulator, false);
        int top = renderer.mFontLineSpacingAndAscent;
        int fg = Color.rgb(100, 50, 20), bg = Color.rgb(20, 40, 80);
        for (int col = 0; col < 3; col++) {
            int x = Math.round((col + .5f) * renderer.mFontWidth);
            assertEquals(col == 1 ? bg : fg, bitmap.getPixel(x, top + 2));
            assertEquals(col == 1 ? fg : bg, bitmap.getPixel(x, top + renderer.mFontLineSpacing - 2));
        }
        Bitmap selection = render(renderer, emulator, true);
        assertEquals(bg, selection.getPixel(2, top + 2));
        assertEquals(fg, selection.getPixel(2, top + renderer.mFontLineSpacing - 2));
        Bitmap hidden = render(renderer, emulator(8, 4, "\033[8m█"), false);
        assertEquals(Color.BLACK, hidden.getPixel(2, top + 2));
    }

    @Test
    public void spaceBackgroundsAndBlockForegroundsShareTheSameBoundaries() {
        StringBuilder text = new StringBuilder();
        for (int col = 0; col < 24; col++) {
            text.append(col % 2 == 0 ? "\033[0;38;2;240;80;40m█" : "\033[0;48;2;40;120;240m ");
        }
        TerminalRenderer renderer = new TerminalRenderer(20.25f, Typeface.MONOSPACE, 1f);
        Bitmap bitmap = render(renderer, emulator(24, 4, text.toString()), false);
        int top = renderer.mFontLineSpacingAndAscent;
        for (int col = 0; col < 24; col++) {
            int expected = col % 2 == 0 ? Color.rgb(240, 80, 40) : Color.rgb(40, 120, 240);
            for (int x = Math.round(col * renderer.mFontWidth); x < Math.round((col + 1) * renderer.mFontWidth); x++)
                for (int y = top; y < top + renderer.mFontLineSpacing; y++) assertEquals(expected, bitmap.getPixel(x, y));
        }
    }

    @Test
    public void strokeWeightIsStableAcrossAlternatingWidthsAndCornersDoNotProtrude() {
        Bitmap narrow = glyph(0x2500, 11, 24), wide = glyph(0x2500, 12, 24);
        for (int y = 0; y < narrow.getHeight(); y++)
            assertEquals(narrow.getPixel(4, y), wide.getPixel(4, y));
        Bitmap corner = glyph(0x250C, 24, 40);
        // Three-pixel light strokes at this size: left/top bounds must both be center - 1.
        assertEquals(0, corner.getPixel(4 + 10, 4 + 20));
        assertEquals(0, corner.getPixel(4 + 12, 4 + 18));
        assertEquals(Color.WHITE, corner.getPixel(4 + 11, 4 + 19));
        Bitmap powerline = glyph(0xE0D7, 24, 40);
        assertEquals(0, powerline.getPixel(4 + 2, 4 + 20));
        assertEquals(Color.WHITE, powerline.getPixel(4 + 22, 4 + 2));
    }

    @Test
    public void exportGlyphAtlasesForVisualReview() throws Exception {
        List<Integer> chars = repertoire();
        File directory = new File("build/glyph-atlas");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        for (int page = 0; page * 256 < chars.size(); page++) {
            Bitmap bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xFF101010);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setTypeface(Typeface.MONOSPACE);
            paint.setTextSize(10);
            for (int i = 0; i < 256 && page * 256 + i < chars.size(); i++) {
                int cp = chars.get(page * 256 + i), x = i % 16 * 64, y = i / 16 * 64;
                paint.setColor(0xFF888888);
                paint.setAntiAlias(true);
                canvas.drawText(String.format("%05X", cp), x + 4, y + 12, paint);
                paint.setColor(0xFF69DDBB);
                canvas.save();
                canvas.translate(x + 16, y + 18);
                drawGeometry(canvas, cp, 24, 40, paint);
                canvas.restore();
            }
            try (FileOutputStream stream = new FileOutputStream(new File(directory, "glyphs-" + (page + 1) + ".png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
        }
    }
}
