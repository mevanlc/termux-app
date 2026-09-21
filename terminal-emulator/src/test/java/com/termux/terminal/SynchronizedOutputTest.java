package com.termux.terminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SynchronizedOutputTest extends TerminalTestCase {
    public void testEndNotifiesOnceAndRepeatedStartKeepsSnapshot() {
        List<Boolean> transitions = new ArrayList<>();
        mOutput = new MockTerminalOutput() {
            @Override public void onSyncUpdate(boolean active) { transitions.add(active); }
        };
        withTerminalSized(4, 3);
        enterString("old\033[?2026h\rnew\033[?2026h");
        assertEquals("old", mTerminal.getScreenForRendering().getTranscriptText());
        enterString("\033[?2026l\033[?2026l");
        assertEquals(Arrays.asList(true, true, false), transitions);
        assertEquals("new", mTerminal.getScreenForRendering().getTranscriptText());
    }

    public void testPaletteAndCursorAppearanceArePublishedTogether() {
        withTerminalSized(4, 3);
        enterString("old");
        int color = mTerminal.mColors.mCurrentColors[1];
        int cursorStyle = mTerminal.getCursorStyle();
        int colorNotifications = mOutput.colorsChanged;
        enterString("\033[?2026h\033]4;1;#123456\007\033[?5h\033[?25l\033[5 q\033[2;1H");
        assertEquals(color, mTerminal.getColorsForRendering()[1]);
        assertEquals(colorNotifications, mOutput.colorsChanged);
        assertFalse(mTerminal.isReverseVideoForRendering());
        assertTrue(mTerminal.isCursorVisibleForRendering());
        assertEquals(cursorStyle, mTerminal.getCursorStyleForRendering());
        assertEquals(0, mTerminal.getCursorRowForRendering());
        assertEquals(3, mTerminal.getCursorColForRendering());
        enterString("\033[?2026l");
        assertEquals(0xff123456, mTerminal.getColorsForRendering()[1]);
        assertEquals(colorNotifications + 1, mOutput.colorsChanged);
        assertTrue(mTerminal.isReverseVideoForRendering());
        assertFalse(mTerminal.isCursorVisibleForRendering());
        assertEquals(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR, mTerminal.getCursorStyleForRendering());
        assertEquals(1, mTerminal.getCursorRowForRendering());
    }

    public void testSnapshotSurvivesCellCopyAttributeAndWrapChanges() {
        withTerminalSized(4, 3).enterString("a界e\u0301\r\nnext");
        TerminalBuffer live = mTerminal.getScreen();
        live.setLineWrap(0);
        enterString("\033[?2026h");
        TerminalBuffer snapshot = mTerminal.getScreenForRendering();
        String text = snapshot.getTranscriptText();
        long style = snapshot.getStyleAt(0, 0);
        live.setOrClearEffect(TextStyle.CHARACTER_ATTRIBUTE_BOLD, true, false, true, 0, 4, 0, 0, 1, 4);
        live.clearLineWrap(0);
        live.blockCopy(0, 1, 4, 1, 0, 0);
        live.setChar(1, 1, 'X', TextStyle.NORMAL);
        live.blockSet(0, 2, 4, 1, 'Z', TextStyle.NORMAL);
        assertEquals(text, snapshot.getTranscriptText());
        assertEquals(style, snapshot.getStyleAt(0, 0));
        assertTrue(snapshot.getLineWrap(0));
        assertFalse(live.getLineWrap(0));
        assertFalse(text.equals(live.getTranscriptText()));
    }

    public void testSnapshotRetainsHistoryWhenRowsAreReusedAndTranscriptCleared() {
        TerminalBuffer live = new TerminalBuffer(4, 6, 3);
        for (int i = 0; i < 9; i++) {
            live.scrollDownOneLine(0, 3, TextStyle.NORMAL);
            live.setChar(0, 2, 'A' + i, TextStyle.NORMAL);
        }
        TerminalBuffer snapshot = live.snapshot();
        String text = snapshot.getTranscriptText();
        for (int i = 0; i < 9; i++) {
            live.scrollDownOneLine(0, 3, TextStyle.NORMAL);
            live.setChar(0, 2, 'a' + i, TextStyle.NORMAL);
        }
        live.clearTranscript();
        assertEquals(text, snapshot.getTranscriptText());
        assertEquals(3, snapshot.getActiveTranscriptRows());
        assertEquals(0, live.getActiveTranscriptRows());
    }

    public void testBufferSwitchKeepsFrozenHistoryUntilRelease() {
        withTerminalSized(4, 3).enterString("aaa\r\nbbb\r\nccc\r\nddd\033[?2026h");
        TerminalBuffer snapshot = mTerminal.getScreenForRendering();
        String text = snapshot.getTranscriptText();
        enterString("\033[?1049h\033[3Jnew");
        assertEquals(0, mTerminal.getScreen().getActiveTranscriptRows());
        assertEquals("aaa", snapshot.getSelectedText(0, -1, 4, -1));
        assertEquals(text, mTerminal.getScreenForRendering().getTranscriptText());
        enterString("\033[?2026l");
        assertSame(mTerminal.getScreen(), mTerminal.getScreenForRendering());
        assertEquals(0, mTerminal.getScreenForRendering().getActiveTranscriptRows());
    }

    public void testResizeReleasesSnapshotAndNextUpdateUsesNewDimensions() {
        withTerminalSized(4, 3).enterString("old\033[?2026h\rnew");
        resize(6, 4);
        assertFalse(mTerminal.isSyncUpdate());
        enterString("\033[?2026h");
        assertEquals(6, mTerminal.getScreenForRendering().mColumns);
        assertEquals(4, mTerminal.getScreenForRendering().mScreenRows);
        mTerminal.reset();
        assertFalse(mTerminal.isSyncUpdate());
    }
}
