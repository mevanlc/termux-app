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

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

import java.util.HashMap;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 * <p/>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
public final class TerminalRenderer {

    final int mTextSize;
    final Typeface mTypeface;
    final float mBrightness;
    private final Paint mTextPaint = new Paint();
    private final Paint mBitmapPaint = new Paint();

    /** The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on a single 'X'. */
    final float mFontWidth;
    /** The {@link Paint#getFontSpacing()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    final int mFontLineSpacing;
    /** The {@link Paint#ascent()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png */
    private final int mFontAscent;
    /** The {@link #mFontLineSpacing} + {@link #mFontAscent}. */
    final int mFontLineSpacingAndAscent;

    private final float[] asciiMeasures = new float[127];
    private final HashMap<Integer, BlockGlyphGeometry> mBlockGlyphGeometryCache = new HashMap<>();
    private final Path mBlockGlyphBatchPath = new Path();
    private boolean mBlockGlyphBatchActive;
    private int mBlockGlyphBatchColor;

    public TerminalRenderer(int textSize, Typeface typeface, float brightness) {
        mTextSize = textSize;
        mTypeface = typeface;
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
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();
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
                    Bitmap bitmap = mEmulator.getScreen().getSixelBitmap(style);
                    if (bitmap != null) {
                        flushBlockGlyphBatch(canvas);
                        float left = column * mFontWidth;
                        float top = heightOffset - mFontLineSpacing;
                        Rect bitmapSrcRect = mEmulator.getScreen().getSixelRect(style);
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
                final int blockGlyphSpec = BlockGlyphs.getSpec(codePoint);
                if (blockGlyphSpec != BlockGlyphs.UNSUPPORTED) {
                    if (lastRunStartColumn >= 0 && column > lastRunStartColumn) {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = false;
                        if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                            invertCursorTextColor = true;
                        }
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun,
                            lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                            cursorColor, cursorShape, lastRunStyle, reverseVideo || invertCursorTextColor || lastRunInsideSelection);
                    }

                    int cursorColor = insideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                    boolean invertCursorTextColor = false;
                    if (insideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                        invertCursorTextColor = true;
                    }
                    drawBlockGlyph(canvas, blockGlyphSpec, palette, heightOffset, column, codePointWcWidth,
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
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
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
            int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
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
        float right = left + runWidthColumns * mFontWidth;

        mes = mes / mFontWidth;
        boolean savedMatrix = false;
        if (Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            // Only draw non-default background.
            mTextPaint.setColor(applyBrightness(backColor, palette[TextStyle.COLOR_INDEX_BACKGROUND]));
            canvas.drawRect(left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint);
        }

        if (cursor != 0) {
            mTextPaint.setColor(applyBrightness(cursor, palette[TextStyle.COLOR_INDEX_BACKGROUND]));
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.f;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) right -= (((right - left) * 3) / 4.f);
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
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

            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(applyBrightness(foreColor, palette[TextStyle.COLOR_INDEX_BACKGROUND]));

            // The text alignment is the default Paint.Align.LEFT.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscent, false, mTextPaint);
            } else {
                canvas.drawText(text, startCharIndex, runWidthChars, left, y - mFontLineSpacingAndAscent, mTextPaint);
            }
        }
        if (savedMatrix) canvas.restore();
    }

    private void drawBlockGlyph(Canvas canvas, int blockGlyphSpec, int[] palette, float y, int startColumn, int runWidthColumns,
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

        float left = startColumn * mFontWidth;
        float right = left + runWidthColumns * mFontWidth;
        float glyphRight = right;
        float top = y - mFontLineSpacing;
        float bottom = y;

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            mTextPaint.setColor(applyBrightness(backColor, palette[TextStyle.COLOR_INDEX_BACKGROUND]));
            canvas.drawRect(left, top, right, bottom, mTextPaint);
        }

        if (cursor != 0) {
            mTextPaint.setColor(applyBrightness(cursor, palette[TextStyle.COLOR_INDEX_BACKGROUND]));
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            float cursorRight = right;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.f;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) cursorRight -= (((cursorRight - left) * 3) / 4.f);
            canvas.drawRect(left, y - cursorHeight, cursorRight, y, mTextPaint);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }

            if (cursor != 0 || underline || strikeThrough) {
                flushBlockGlyphBatch(canvas);
                mTextPaint.setColor(applyBrightness(foreColor, palette[TextStyle.COLOR_INDEX_BACKGROUND]));
                drawBlockGlyphCells(canvas, blockGlyphSpec, left, top, runWidthColumns);

                if (underline || strikeThrough) {
                    float lineHeight = Math.max(1.f, mTextSize / 12.f);
                    if (underline) {
                        float underlineTop = Math.max(top, y - lineHeight);
                        canvas.drawRect(left, underlineTop, glyphRight, y, mTextPaint);
                    }
                    if (strikeThrough) {
                        float center = top + ((bottom - top) / 2.f);
                        canvas.drawRect(left, center - lineHeight / 2.f, glyphRight, center + lineHeight / 2.f, mTextPaint);
                    }
                }
            } else {
                appendBlockGlyphCellsToBatch(canvas, blockGlyphSpec, applyBrightness(foreColor, palette[TextStyle.COLOR_INDEX_BACKGROUND]), left, top, runWidthColumns);
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

    private void drawBlockGlyphCells(Canvas canvas, int blockGlyphSpec, float left, float top, int runWidthColumns) {
        BlockGlyphGeometry geometry = getBlockGlyphGeometry(blockGlyphSpec, runWidthColumns);
        float[] rects = geometry.rects;
        for (int i = 0; i < rects.length; i += 4)
            canvas.drawRect(left + rects[i], top + rects[i + 1], left + rects[i + 2], top + rects[i + 3], mTextPaint);
    }

    private void appendBlockGlyphCellsToBatch(Canvas canvas, int blockGlyphSpec, int color, float left, float top, int runWidthColumns) {
        if (mBlockGlyphBatchActive && mBlockGlyphBatchColor != color)
            flushBlockGlyphBatch(canvas);

        if (!mBlockGlyphBatchActive) {
            mBlockGlyphBatchPath.reset();
            mBlockGlyphBatchColor = color;
            mBlockGlyphBatchActive = true;
        }

        BlockGlyphGeometry geometry = getBlockGlyphGeometry(blockGlyphSpec, runWidthColumns);
        float[] rects = geometry.rects;
        for (int i = 0; i < rects.length; i += 4) {
            mBlockGlyphBatchPath.addRect(left + rects[i], top + rects[i + 1],
                left + rects[i + 2], top + rects[i + 3], Path.Direction.CW);
        }
    }

    private void flushBlockGlyphBatch(Canvas canvas) {
        if (!mBlockGlyphBatchActive)
            return;

        mTextPaint.setColor(mBlockGlyphBatchColor);
        canvas.drawPath(mBlockGlyphBatchPath, mTextPaint);
        mBlockGlyphBatchPath.reset();
        mBlockGlyphBatchActive = false;
    }

    private BlockGlyphGeometry getBlockGlyphGeometry(int blockGlyphSpec, int runWidthColumns) {
        int key = blockGlyphSpec | (runWidthColumns << 28);
        BlockGlyphGeometry geometry = mBlockGlyphGeometryCache.get(key);
        if (geometry != null)
            return geometry;

        final int mask = BlockGlyphs.getMask(blockGlyphSpec);
        final int columns = BlockGlyphs.getColumns(blockGlyphSpec);
        final int rows = BlockGlyphs.getRows(blockGlyphSpec);
        final boolean separated = BlockGlyphs.isSeparated(blockGlyphSpec);
        final float width = runWidthColumns * mFontWidth;
        final float height = mFontLineSpacing;
        final float gapX = separated ? Math.max(1.f, width / 8.f) : 0.f;
        final float gapY = separated ? Math.max(1.f, height / 8.f) : 0.f;
        final float cellWidth = separated ? Math.max(0.f, (width - (columns + 1) * gapX) / columns) : width / columns;
        final float cellHeight = separated ? Math.max(0.f, (height - (rows + 1) * gapY) / rows) : height / rows;
        final float[] rects = new float[Integer.bitCount(mask) * 4];
        int rectOffset = 0;

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if ((mask & (1 << (row * columns + column))) == 0)
                    continue;

                float cellLeft;
                float cellTop;
                float cellRight;
                float cellBottom;
                if (separated) {
                    cellLeft = gapX + column * (cellWidth + gapX);
                    cellTop = gapY + row * (cellHeight + gapY);
                    cellRight = cellLeft + cellWidth;
                    cellBottom = cellTop + cellHeight;
                } else {
                    cellLeft = column * cellWidth;
                    cellTop = row * cellHeight;
                    cellRight = column == columns - 1 ? width : (column + 1) * cellWidth;
                    cellBottom = row == rows - 1 ? height : (row + 1) * cellHeight;
                }
                rects[rectOffset++] = cellLeft;
                rects[rectOffset++] = cellTop;
                rects[rectOffset++] = cellRight;
                rects[rectOffset++] = cellBottom;
            }
        }

        geometry = new BlockGlyphGeometry(rects);
        mBlockGlyphGeometryCache.put(key, geometry);
        return geometry;
    }

    private static final class BlockGlyphGeometry {
        final float[] rects;

        BlockGlyphGeometry(float[] rects) {
            this.rects = rects;
        }
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }
}
