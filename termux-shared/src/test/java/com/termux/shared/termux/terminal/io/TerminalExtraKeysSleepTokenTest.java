package com.termux.shared.termux.terminal.io;

import com.termux.shared.termux.extrakeys.SpecialButton;

import org.junit.Test;

import static org.junit.Assert.*;

public class TerminalExtraKeysSleepTokenTest {

    @Test
    public void parseSleepMillis_acceptsValid() {
        assertEquals(Integer.valueOf(0), TerminalExtraKeys.parseSleepMillis("SLEEP0"));
        assertEquals(Integer.valueOf(1), TerminalExtraKeys.parseSleepMillis("SLEEP1"));
        assertEquals(Integer.valueOf(250), TerminalExtraKeys.parseSleepMillis("SLEEP250"));
        assertEquals(Integer.valueOf(9999), TerminalExtraKeys.parseSleepMillis("SLEEP9999"));
    }

    @Test
    public void parseSleepMillis_rejectsInvalid() {
        assertNull(TerminalExtraKeys.parseSleepMillis("SLEEP"));
        assertNull(TerminalExtraKeys.parseSleepMillis("SLEEP10000"));
        assertNull(TerminalExtraKeys.parseSleepMillis("SLEEP-1"));
        assertNull(TerminalExtraKeys.parseSleepMillis("SLEEP1x"));
        assertNull(TerminalExtraKeys.parseSleepMillis("sleep250"));
        assertNull(TerminalExtraKeys.parseSleepMillis("XSLEEP250"));
    }

    @Test
    public void parseMacro_detectsSleepAndKeepsTokens() {
        TerminalExtraKeys.ParsedMacro parsed = TerminalExtraKeys.parseMacro("CTRL SLEEP250 c");
        assertTrue(parsed.usesSleep);
        assertEquals(3, parsed.steps.length);

        assertEquals(TerminalExtraKeys.MacroStepType.MODIFIER, parsed.steps[0].type);
        assertEquals(SpecialButton.CTRL, parsed.steps[0].modifier);

        assertEquals(TerminalExtraKeys.MacroStepType.SLEEP, parsed.steps[1].type);
        assertEquals(250, parsed.steps[1].sleepMillis);

        assertEquals(TerminalExtraKeys.MacroStepType.KEY, parsed.steps[2].type);
        assertEquals("c", parsed.steps[2].key);
    }

    @Test
    public void parseMacro_lowercaseSleepIsLiteralKey() {
        TerminalExtraKeys.ParsedMacro parsed = TerminalExtraKeys.parseMacro("CTRL sleep250 c");
        assertFalse(parsed.usesSleep);
        assertEquals(3, parsed.steps.length);
        assertEquals(TerminalExtraKeys.MacroStepType.MODIFIER, parsed.steps[0].type);
        assertEquals(TerminalExtraKeys.MacroStepType.KEY, parsed.steps[1].type);
        assertEquals("sleep250", parsed.steps[1].key);
        assertEquals(TerminalExtraKeys.MacroStepType.KEY, parsed.steps[2].type);
        assertEquals("c", parsed.steps[2].key);
    }
}

