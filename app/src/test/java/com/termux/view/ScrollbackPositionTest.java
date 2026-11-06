package com.termux.view;

import android.app.Application;

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
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, shadows = ScrollbackPositionTest.ShadowJNI.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ScrollbackPositionTest {
    @Test
    public void sessionSwitchesRestoreIndependentPositionsAndFractionalFontSizes() {
        TerminalView view = createView();
        TerminalSession first = createSession();
        TerminalSession second = createSession();
        view.attachSession(first, 20.25f);
        view.doScroll(null, -2);

        view.attachSession(second, 22.5f);
        assertEquals(0, view.getTopRow());
        view.doScroll(null, -1);

        view.attachSession(first, 20.25f);
        assertEquals(-2, view.getTopRow());
        assertEquals(20.25f, view.getTextSize(), 0f);
        view.attachSession(second, 22.5f);
        assertEquals(-1, view.getTopRow());
        assertEquals(22.5f, view.getTextSize(), 0f);
    }

    @Test
    public void recreatedViewRestoresSessionPosition() {
        TerminalView original = createView();
        TerminalSession session = createSession();
        original.attachSession(session);
        original.doScroll(null, -2);

        TerminalView recreated = createView();
        recreated.attachSession(session);
        assertEquals(-2, recreated.getTopRow());
    }

    @Test
    public void changingVisibleRowsPreservesScrollPosition() {
        TerminalView view = createView();
        view.attachSession(createSession());
        view.doScroll(null, -2);

        int height = 6 * view.mRenderer.mFontLineSpacing + view.mRenderer.mFontLineSpacingAndAscent;
        view.layout(0, 0, 1, height);
        assertEquals(6, view.mEmulator.mRows);
        assertEquals(-2, view.getTopRow());
        view.layout(0, 0, 1, 1);
        assertEquals(4, view.mEmulator.mRows);
        assertEquals(-2, view.getTopRow());
    }

    @Test
    public void sessionSwitchDuringSynchronizedOutputUsesFrozenHistory() {
        TerminalView view = createView();
        TerminalSession first = createSession();
        TerminalSession second = createSession();
        view.attachSession(first);
        view.doScroll(null, -2);

        append(first, "\033[?2026h\033[3J");
        assertEquals(0, first.getEmulator().getScreen().getActiveTranscriptRows());
        view.doScroll(null, -1);
        assertEquals(-3, view.getTopRow());

        view.attachSession(second);
        view.attachSession(first);
        assertTrue(first.getEmulator().isSyncUpdate());
        assertEquals(-3, view.getTopRow());
        view.onScreenUpdated();
        assertEquals(-3, view.getTopRow());

        append(first, "\033[?2026l");
        view.onScreenUpdated();
        assertEquals(0, view.getTopRow());
        view.attachSession(second);
        view.attachSession(first);
        assertEquals(0, view.getTopRow());
    }

    private TerminalView createView() {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTerminalViewClient(new TermuxTerminalViewClientBase());
        view.setTextSize(20.25f);
        view.layout(0, 0, 1, 1);
        return view;
    }

    private TerminalSession createSession() {
        TermuxTerminalSessionClientBase client = new TermuxTerminalSessionClientBase();
        TerminalSession session = new TerminalSession("/unused", "/", new String[0], new String[0], null, client);
        TerminalEmulator emulator = new TerminalEmulator(session, 4, 4, 1, 1, null, client);
        ReflectionHelpers.setField(session, "mEmulator", emulator);
        append(session, "aaa\r\nbbb\r\nccc\r\nddd\r\neee\r\nfff\r\nggg\r\nhhh\r\niii\r\njjj");
        emulator.clearScrollCounter();
        return session;
    }

    private void append(TerminalSession session, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        session.getEmulator().append(bytes, bytes.length);
    }

    /** Exercise real emulator resizing without opening a native pty. */
    @Implements(className = "com.termux.terminal.JNI")
    public static class ShadowJNI {
        @Implementation
        protected static void __staticInitializer__() {}

        @Implementation
        protected static void setPtyWindowSize(int fd, int rows, int columns, int cellWidth, int cellHeight) {}
    }
}
