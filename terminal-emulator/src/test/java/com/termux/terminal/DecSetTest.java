package com.termux.terminal;

/**
 * <pre>
 * "CSI ? Pm h", DEC Private Mode Set (DECSET)
 * </pre>
 * <p/>
 * and
 * <p/>
 * <pre>
 * "CSI ? Pm l", DEC Private Mode Reset (DECRST)
 * </pre>
 * <p/>
 * controls various aspects of the terminal
 */
public class DecSetTest extends TerminalTestCase {

	/** DECSET 25, DECTCEM, controls visibility of the cursor. */
	public void testEnableDisableCursor() {
		withTerminalSized(3, 3);
		assertTrue("Initially the cursor should be enabled", mTerminal.isCursorEnabled());
		enterString("\033[?25l"); // Disable Cursor (DECTCEM).
		assertFalse(mTerminal.isCursorEnabled());
		enterString("\033[?25h"); // Enable Cursor (DECTCEM).
		assertTrue(mTerminal.isCursorEnabled());

		enterString("\033[?25l"); // Disable Cursor (DECTCEM), again.
		assertFalse(mTerminal.isCursorEnabled());
		mTerminal.reset();
		assertTrue("Resetting the terminal should enable the cursor", mTerminal.isCursorEnabled());

		enterString("\033[?25l");
		assertFalse(mTerminal.isCursorEnabled());
		enterString("\033c"); // RIS resetting should enabled cursor.
		assertTrue(mTerminal.isCursorEnabled());
	}

	/** DECSET 2004, controls bracketed paste mode. */
	public void testBracketedPasteMode() {
		withTerminalSized(3, 3);

		mTerminal.paste("a");
		assertEquals("Pasting 'a' should output 'a' when bracketed paste mode is disabled", "a", mOutput.getOutputAndClear());

		enterString("\033[?2004h"); // Enable bracketed paste mode.
		mTerminal.paste("a");
		assertEquals("Pasting when in bracketed paste mode should be bracketed", "\033[200~a\033[201~", mOutput.getOutputAndClear());

		enterString("\033[?2004l"); // Disable bracketed paste mode.
		mTerminal.paste("a");
		assertEquals("Pasting 'a' should output 'a' when bracketed paste mode is disabled", "a", mOutput.getOutputAndClear());

		enterString("\033[?2004h"); // Enable bracketed paste mode, again.
		mTerminal.paste("a");
		assertEquals("Pasting when in bracketed paste mode again should be bracketed", "\033[200~a\033[201~", mOutput.getOutputAndClear());

		mTerminal.paste("\033ab\033cd\033");
		assertEquals("Pasting an escape character should not input it", "\033[200~abcd\033[201~", mOutput.getOutputAndClear());
		mTerminal.paste("\u0081ab\u0081cd\u009F");
		assertEquals("Pasting C1 control codes should not input it", "\033[200~abcd\033[201~", mOutput.getOutputAndClear());

		mTerminal.reset();
		mTerminal.paste("a");
		assertEquals("Terminal reset() should disable bracketed paste mode", "a", mOutput.getOutputAndClear());
	}

	/** DECSET 7, DECAWM, controls wraparound mode. */
	public void testWrapAroundMode() {
		// Default with wraparound:
		withTerminalSized(3, 3).enterString("abcd").assertLinesAre("abc", "d  ", "   ");
		// With wraparound disabled:
		withTerminalSized(3, 3).enterString("\033[?7labcd").assertLinesAre("abd", "   ", "   ");
		enterString("efg").assertLinesAre("abg", "   ", "   ");
		// Re-enabling wraparound:
		enterString("\033[?7hhij").assertLinesAre("abh", "ij ", "   ");
	}

	/** DECSET 2026, controls synchronized update mode. */
	public void testSynchronizedUpdateMode() {
		withTerminalSized(3, 3);
		assertFalse("Initially synchronized update should be disabled", mTerminal.isSyncUpdate());

		// Query mode status (DECRQM) -> 2 = reset
		enterString("\033[?2026$p");
		assertEquals("\033[?2026;2$y", mOutput.getOutputAndClear());

		// Write initial text
		enterString("abc");
		assertEquals("abc", mTerminal.getScreen().getSelectedText(0, 0, 3, 1).trim());

		// Enter Mode 2026
		enterString("\033[?2026h");
		assertTrue("Synchronized update should be enabled", mTerminal.isSyncUpdate());
		assertEquals(0, mTerminal.getCursorRowForRendering());
		assertEquals(2, mTerminal.getCursorColForRendering());
		TerminalBuffer snapshot = mTerminal.getScreenForRendering();
		assertEquals("abc", snapshot.getSelectedText(0, 0, 3, 0));

		// Query mode status (DECRQM) -> 1 = set
		enterString("\033[?2026$p");
		assertEquals("\033[?2026;1$y", mOutput.getOutputAndClear());

		// Write text while in sync mode: live screen changes, but snapshot remains frozen
		enterString("\rxyz");
		// Live screen has xyz
		assertEquals("xyz", mTerminal.getScreen().getSelectedText(0, 0, 3, 1).trim());
		// Snapshot still has abc
		assertEquals("abc", snapshot.getSelectedText(0, 0, 3, 0));

		// Exit Mode 2026
		enterString("\033[?2026l");
		assertFalse("Synchronized update should be disabled", mTerminal.isSyncUpdate());

		// Query mode status (DECRQM) -> 2 = reset
		enterString("\033[?2026$p");
		assertEquals("\033[?2026;2$y", mOutput.getOutputAndClear());

		// Test reset() clears sync mode
		enterString("\033[?2026h");
		assertTrue(mTerminal.isSyncUpdate());
		mTerminal.reset();
		assertFalse("reset() should disable synchronized update", mTerminal.isSyncUpdate());

		// Test soft reset (\033[!p) clears sync mode
		enterString("\033[?2026h");
		assertTrue(mTerminal.isSyncUpdate());
		enterString("\033[!p");
		assertFalse("Soft reset should disable synchronized update", mTerminal.isSyncUpdate());
	}

}
