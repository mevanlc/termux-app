package com.termux.view;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;

import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TextStyle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SynchronizedRenderingTest {
    private TerminalEmulator emulator;
    private TerminalView view;

    @Before
    public void setUp() {
        TerminalOutput output = new TerminalOutput() {
            public void write(byte[] data, int offset, int count) {}
            public void titleChanged(String oldTitle, String newTitle) {}
            public void onCopyTextToClipboard(String text) {}
            public void onPasteTextFromClipboard() {}
            public void onBell() {}
            public void onColorsChanged() {}
        };
        emulator = new TerminalEmulator(output, 8, 4, 10, 10, 10, null);
        view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTerminalViewClient(new TermuxTerminalViewClientBase());
        view.setTextSize(20f);
        view.setTypeface(Typeface.MONOSPACE);
        view.mEmulator = emulator;
    }

    private void append(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
    }

    private Bitmap render() {
        Bitmap bitmap = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(emulator.getColorsForRendering()[TextStyle.COLOR_INDEX_BACKGROUND]);
        view.onDraw(new Canvas(bitmap));
        return bitmap;
    }

    @Test
    public void scrolledViewportStaysFrozenAcrossBufferSwitchAndHistoryClear() {
        append("aaa\r\nbbb\r\nccc\r\nddd\r\neee");
        emulator.clearScrollCounter();
        view.mTopRow = -1;
        Bitmap before = render();
        append("\033[?2026h\033[?1049h\033[3Jnew");
        view.onScreenUpdated();
        assertEquals(-1, view.mTopRow);
        assertTrue(before.sameAs(render()));
        append("\033[?2026l");
        view.onScreenUpdated();
        assertEquals(0, view.mTopRow);
        assertFalse(before.sameAs(render()));
    }

    @Test
    public void paletteBackgroundReverseVideoAndCursorStayFrozen() {
        append("\033[31mred");
        Bitmap before = render();
        append("\033[?2026h\033]4;1;#00ff00\007\033]11;#112233\007" +
            "\033]12;#abcdef\007\033[?5h\033[5 q\033[2;1H");
        assertTrue(before.sameAs(render()));
        append("\033[?2026l");
        assertFalse(before.sameAs(render()));
    }

    private void addImage(int color) {
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes));
        assertNotNull(emulator.getScreen().addTerminalBitmapForImage(bytes.toByteArray(), 0, 0, 10, 10, 10, 10, false));
    }

    private void assertContainsColor(Bitmap bitmap, int color) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int pixel : pixels) if (pixel == color) return;
        fail("Rendered image did not contain color " + Integer.toHexString(color));
    }

    @Test
    public void bitmapOwnershipSurvivesRemovalIdReuseAndBufferSwitch() {
        append("\033[?25l");
        addImage(Color.RED);
        Bitmap before = render();
        assertContainsColor(before, Color.RED);
        append("\033[?2026h");
        TerminalBuffer snapshot = emulator.getScreenForRendering();
        long style = snapshot.getStyleAt(0, 0);
        Bitmap original = snapshot.getSixelBitmap(style);
        emulator.getScreen().clearTerminalBitmaps();
        addImage(Color.BLUE);
        assertNotSame(original, emulator.getScreen().getSixelBitmap(style));
        assertSame(original, snapshot.getSixelBitmap(style));
        assertTrue(before.sameAs(render()));
        append("\033[?1049h");
        addImage(Color.GREEN);
        assertTrue(before.sameAs(render()));
        append("\033[?2026l");
        assertContainsColor(render(), Color.GREEN);
    }
}
