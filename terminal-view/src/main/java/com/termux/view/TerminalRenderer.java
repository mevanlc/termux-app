package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.LruCache;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 * <p/>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
public final class TerminalRenderer {

    final float mTextSize;
    final Typeface mTypeface;
    /** Variant typefaces for styled text. Equal to {@link #mTypeface} when no separate variant font is available. */
    final Typeface mBoldTypeface;
    final Typeface mItalicTypeface;
    final Typeface mBoldItalicTypeface;
    final float mBrightness;
    private final Paint mTextPaint = new Paint();
    private final Paint mBitmapPaint = new Paint();
    private final Paint mGlyphPaint = new Paint();

    /** The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on a single 'X'. */
    final float mFontWidth;
    /** The {@link Paint#getFontSpacing()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    final int mFontLineSpacing;
    /** The {@link Paint#ascent()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    private final int mFontAscent;
    /** The {@link #mFontLineSpacing} + {@link #mFontAscent}. */
    final int mFontLineSpacingAndAscent;

    private final float[] asciiMeasures = new float[127];
    private final LruCache<Long, TerminalGlyphs.Geometry> mGlyphGeometryCache = new LruCache<>(512);
    private final Path mBlockGlyphBatchPath = new Path();
    private boolean mBlockGlyphBatchActive;
    private int mBlockGlyphBatchColor;

    public TerminalRenderer(float textSize, Typeface typeface, float brightness) {
        this(textSize, typeface, null, null, null, brightness);
    }

    public TerminalRenderer(float textSize, Typeface typeface, Typeface boldTypeface, Typeface italicTypeface,
                            Typeface boldItalicTypeface, float brightness) {
        mTextSize = textSize;
        mTypeface = typeface;
        mBoldTypeface = boldTypeface == null ? typeface : boldTypeface;
        mItalicTypeface = italicTypeface == null ? typeface : italicTypeface;
        mBoldItalicTypeface = boldItalicTypeface == null ? typeface : boldItalicTypeface;
        mBrightness = brightness;

        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);
        if (brightness != 1.f) {
            mBitmapPaint.setColorFilter(new ColorMatrixColorFilter(new float[] {
                brightness, 0, 0, 0, 0,
                0, brightness, 0, 0, 0,
                0, 0, brightness, 0, 0,
                0, 0, 0, 1, 0
            }));
        }

        mFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing());
        mFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;
        mFontWidth = mTextPaint.measureText("X");

        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
    }

    /** Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection. */
    public final void render(TerminalEmulator mEmulator, Canvas canvas, int topRow,
                             int selectionY1, int selectionY2, int selectionX1, int selectionX2) {
        final boolean reverseVideo = mEmulator.isReverseVideoForRendering();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorColForRendering();
        final int cursorRow = mEmulator.getCursorRowForRendering();
        final boolean cursorVisible = mEmulator.isCursorVisibleForRendering();
        final TerminalBuffer screen = mEmulator.getScreenForRendering();
        final int[] palette = mEmulator.getColorsForRendering();
        final int cursorShape = mEmulator.getCursorStyleForRendering();
        final int defaultBackground = palette[TextStyle.COLOR_INDEX_BACKGROUND];

        if (reverseVideo)
            canvas.drawColor(applyBrightness(palette[TextStyle.COLOR_INDEX_FOREGROUND], defaultBackground), PorterDuff.Mode.SRC);

        float heightOffset = mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacing;

            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1) selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }

            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();

            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;

            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final long style = lineObject.getStyle(column);
                if (TextStyle.isTerminalBitmap(style)) {
                    Bitmap bitmap = screen.getSixelBitmap(style);
                    if (bitmap != null) {
                        flushBlockGlyphBatch(canvas);
                        float left = column * mFontWidth;
                        float top = heightOffset - mFontLineSpacing;
                        Rect bitmapSrcRect = screen.getSixelRect(style);
                        RectF bitmapDestRect = new RectF(left, top, left + mFontWidth, top + mFontLineSpacing);
                        canvas.drawBitmap(bitmap, bitmapSrcRect, bitmapDestRect, mBrightness == 1.f ? null : mBitmapPaint);
                    }
                    column += 1;
                    measuredWidthForRun = 0.f;
                    lastRunStyle = 0;
                    lastRunInsideCursor = false;
                    lastRunStartColumn = column + 1;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = false;
                    currentCharIndex += charsForCodePoint;
                    continue;
                }
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                if (TerminalGlyphs.isSupported(codePoint)) {
                    if (lastRunStartColumn >= 0 && column > lastRunStartColumn) {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? palette[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = false;
                        if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                            invertCursorTextColor = true;
                        }
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun,
                            lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                            cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
                    }

                    int cursorColor = insideCursor ? palette[TextStyle.COLOR_INDEX_CURSOR] : 0;
                    boolean invertCursorTextColor = false;
                    if (insideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                        invertCursorTextColor = true;
                    }
                    drawBlockGlyph(canvas, codePoint, palette, heightOffset, column, codePointWcWidth,
                        cursorColor, cursorShape, style, reverseVideo || invertCursorTextColor || insideSelection);

                    column += codePointWcWidth;
                    currentCharIndex += charsForCodePoint;
                    while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                        currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = 0;
                    lastRunInsideCursor = false;
                    lastRunInsideSelection = false;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = false;
                    continue;
                }

                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (codePoint < asciiMeasures.length) ? asciiMeasures[codePoint] : mTextPaint.measureText(line,
                    currentCharIndex, charsForCodePoint);
                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;

                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch) {
                    if (column == 0 || column == lastRunStartColumn) {
                        // Skip first column as there is nothing to draw, just record the current style.
                    } else {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? palette[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = false;
                        if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                            invertCursorTextColor = true;
                        }
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun,
                            lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                            cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }

            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            int cursorColor = lastRunInsideCursor ? palette[TextStyle.COLOR_INDEX_CURSOR] : 0;
            boolean invertCursorTextColor = false;
            if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                invertCursorTextColor = true;
            }
            drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
        }
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns,
                             int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle,
                             long textStyle, boolean reverseVideo) {
        flushBlockGlyphBatch(canvas);

        if (runWidthColumns <= 0 || runWidthChars <= 0)
            return;

        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            // Let bold have bright colors if applicable (one of the first 8):
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        // Reverse video here if _one and only one_ of the reverse flags are set:
        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        float left = startColumn * mFontWidth;
        int cellLeft = Math.round(left);
        int cellRight = Math.round((startColumn + runWidthColumns) * mFontWidth);
        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            // Cell backgrounds use the same shared pixel boundaries as geometric glyphs.
            mGlyphPaint.setColor(applyBrightness(backColor, palette[TextStyle.COLOR_INDEX_BACKGROUND]));
            canvas.drawRect(cellLeft, y - mFontLineSpacing, cellRight, y, mGlyphPaint);
        }

        if (cursor != 0) {
            mGlyphPaint.setColor(applyBrightness(cursor, palette[TextStyle.COLOR_INDEX_BACKGROUND]));
            float cursorHeight = mFontLineSpacing;
            float cursorRight = cellRight;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.f;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) cursorRight = cellLeft + (cellRight - cellLeft) / 4.f;
            canvas.drawRect(cellLeft, y - cursorHeight, cursorRight, y, mGlyphPaint);
        }

        mes = mes / mFontWidth;
        boolean savedMatrix = false;
        if (Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            savedMatrix = true;
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                // Dim color handling used by libvte which in turn took it from xterm
                // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }

            // Prefer real variant typefaces; synthesize bold/italic only for styles no loaded font provides.
            Typeface runTypeface = mTypeface;
            boolean fakeBold = bold;
            float textSkewX = italic ? -0.35f : 0.f;
            if (bold && italic && mBoldItalicTypeface != mTypeface) {
                runTypeface = mBoldItalicTypeface;
                fakeBold = false;
                textSkewX = 0.f;
            } else if (bold && mBoldTypeface != mTypeface) {
                runTypeface = mBoldTypeface;
                fakeBold = false;
            } else if (italic && mItalicTypeface != mTypeface) {
                runTypeface = mItalicTypeface;
                textSkewX = 0.f;
            }

            if (runTypeface != mTypeface) mTextPaint.setTypeface(runTypeface);
            mTextPaint.setFakeBoldText(fakeBold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(textSkewX);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(applyBrightness(foreColor, palette[TextStyle.COLOR_INDEX_BACKGROUND]));

            // The text alignment is the default Paint.Align.LEFT.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscent, false, mTextPaint);
            } else {
                canvas.drawText(text, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscent, mTextPaint);
            }
            // Cell measurement elsewhere in the render loop relies on the paint using the regular typeface.
            if (runTypeface != mTypeface) mTextPaint.setTypeface(mTypeface);
        }
        if (savedMatrix) canvas.restore();
    }

    private void drawBlockGlyph(Canvas canvas, int codePoint, int[] palette, float y, int startColumn, int runWidthColumns,
                                int cursor, int cursorStyle, long textStyle, boolean reverseVideo) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        // Both neighbors calculate the same boundary; rounding an origin plus a width separately
        // would leave seams at fractional zoom levels.
        int left = Math.round(startColumn * mFontWidth);
        int right = Math.round((startColumn + runWidthColumns) * mFontWidth);
        int top = Math.round(y) - mFontLineSpacing;
        int bottom = Math.round(y);
        if (right <= left) return;

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            flushBlockGlyphBatch(canvas);
            mGlyphPaint.setColor(applyBrightness(backColor, palette[TextStyle.COLOR_INDEX_BACKGROUND]));
            canvas.drawRect(left, top, right, bottom, mGlyphPaint);
        }

        if (cursor != 0) {
            flushBlockGlyphBatch(canvas);
            mGlyphPaint.setColor(applyBrightness(cursor, palette[TextStyle.COLOR_INDEX_BACKGROUND]));
            float cursorHeight = mFontLineSpacing;
            float cursorRight = right;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.f;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) cursorRight = left + (right - left) / 4.f;
            canvas.drawRect(left, y - cursorHeight, cursorRight, y, mGlyphPaint);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16)) * 2 / 3;
                int green = (0xFF & (foreColor >> 8)) * 2 / 3;
                int blue = (0xFF & foreColor) * 2 / 3;
                foreColor = 0xFF000000 | (red << 16) | (green << 8) | blue;
            }
            int color = applyBrightness(foreColor, palette[TextStyle.COLOR_INDEX_BACKGROUND]);
            TerminalGlyphs.Geometry geometry = getGlyphGeometry(codePoint, right - left, bottom - top);
            if (cursor != 0 || underline || strikeThrough || !geometry.smooth.isEmpty()) {
                flushBlockGlyphBatch(canvas);
                mGlyphPaint.setColor(color);
                canvas.save();
                canvas.translate(left, top);
                canvas.drawPath(geometry.pixels, mGlyphPaint);
                mGlyphPaint.setAntiAlias(true);
                canvas.drawPath(geometry.smooth, mGlyphPaint);
                mGlyphPaint.setAntiAlias(false);
                canvas.restore();
                float lineHeight = Math.max(1.f, mTextSize / 12.f);
                if (underline) canvas.drawRect(left, Math.max(top, y - lineHeight), right, y, mGlyphPaint);
                if (strikeThrough) {
                    float center = top + (bottom - top) / 2.f;
                    canvas.drawRect(left, center - lineHeight / 2.f, right, center + lineHeight / 2.f, mGlyphPaint);
                }
            } else {
                if (mBlockGlyphBatchActive && mBlockGlyphBatchColor != color) flushBlockGlyphBatch(canvas);
                if (!mBlockGlyphBatchActive) {
                    mBlockGlyphBatchPath.reset();
                    mBlockGlyphBatchColor = color;
                    mBlockGlyphBatchActive = true;
                }
                mBlockGlyphBatchPath.addPath(geometry.pixels, left, top);
            }
        }
    }

    private int applyBrightness(int color, int defaultBackground) {
        if (mBrightness == 1.f || color == defaultBackground)
            return color;

        int red = Math.min(255, Math.max(0, (int) (((0xFF & (color >> 16)) * mBrightness) + 0.5f)));
        int green = Math.min(255, Math.max(0, (int) (((0xFF & (color >> 8)) * mBrightness) + 0.5f)));
        int blue = Math.min(255, Math.max(0, (int) (((0xFF & color) * mBrightness) + 0.5f)));
        return (color & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private void flushBlockGlyphBatch(Canvas canvas) {
        if (!mBlockGlyphBatchActive) return;
        mGlyphPaint.setColor(mBlockGlyphBatchColor);
        canvas.drawPath(mBlockGlyphBatchPath, mGlyphPaint);
        mBlockGlyphBatchPath.reset();
        mBlockGlyphBatchActive = false;
    }

    private TerminalGlyphs.Geometry getGlyphGeometry(int codePoint, int width, int height) {
        long key = ((long) codePoint << 42) | ((long) width << 21) | height;
        TerminalGlyphs.Geometry geometry = mGlyphGeometryCache.get(key);
        if (geometry == null) {
            geometry = TerminalGlyphs.create(codePoint, width, height);
            mGlyphGeometryCache.put(key, geometry);
        }
        return geometry;
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }
}
