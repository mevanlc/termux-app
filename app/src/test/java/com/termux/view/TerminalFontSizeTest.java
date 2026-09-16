package com.termux.view;

import android.app.Application;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, shadows = TerminalFontSizeTest.ShadowJNI.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerminalFontSizeTest {
    @Test
    public void fractionalSizeRedrawsWithoutChangingGridOrScrollPosition() {
        TerminalView view = createView();
        view.mTopRow = -2;
        shadowOf(view).clearWasInvalidated();

        view.setTextSize(20.25f);

        assertEquals(20.25f, view.getTextSize(), 0f);
        assertEquals(20.25f, view.mRenderer.mTextSize, 0f);
        Paint paint = ReflectionHelpers.getField(view.mRenderer, "mTextPaint");
        assertEquals(20.25f, paint.getTextSize(), 0f);
        assertEquals(4, view.mEmulator.mColumns);
        assertEquals(4, view.mEmulator.mRows);
        assertEquals(-2, view.mTopRow);
        assertTrue(shadowOf(view).wasInvalidated());
        view.setTypeface(Typeface.MONOSPACE);
        view.setBrightness(0.8f);
        assertEquals(20.25f, view.getTextSize(), 0f);
    }

    @Test
    public void updatesCellPixelReportsEvenWhenGridIsUnchanged() {
        TerminalView view = createView();
        view.mTopRow = -2;
        view.mEmulator.resize(4, 4, 999, 999);
        ShadowJNI.resizeCalls = 0;

        view.updateSize();

        assertEquals(1, ShadowJNI.resizeCalls);
        assertEquals(Math.max(1, (int) view.mRenderer.getFontWidth()), view.mEmulator.getCellWidthPixels());
        assertEquals(view.mRenderer.getFontLineSpacing(), view.mEmulator.getCellHeightPixels());
        assertEquals(-2, view.mTopRow);
        view.updateSize();
        assertEquals(1, ShadowJNI.resizeCalls);
    }

    private TerminalView createView() {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTerminalViewClient(new TermuxTerminalViewClientBase());
        view.setTextSize(20f);
        // The minimum 4x4 grid stays fixed while the fractional font size changes.
        view.layout(0, 0, 1, 1);
        TermuxTerminalSessionClientBase client = new TermuxTerminalSessionClientBase();
        TerminalSession session = new TerminalSession("/unused", "/", new String[0], new String[0], null, client);
        TerminalEmulator emulator = new TerminalEmulator(session, 4, 4, 1, 1, null, client);
        ReflectionHelpers.setField(session, "mEmulator", emulator);
        view.attachSession(session);
        return view;
    }

    /** Keep real emulator resizing while replacing the Android-only pty call. */
    @Implements(className = "com.termux.terminal.JNI")
    public static class ShadowJNI {
        static int resizeCalls;

        @Implementation
        protected static void __staticInitializer__() {}

        @Implementation
        protected static void setPtyWindowSize(int fd, int rows, int columns, int cellWidth, int cellHeight) {
            resizeCalls++;
        }
    }
}
