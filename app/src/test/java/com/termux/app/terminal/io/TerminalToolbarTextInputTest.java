package com.termux.app.terminal.io;

import android.app.Application;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.termux.R;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, shadows = TerminalToolbarTextInputTest.ShadowTerminalSession.class)
public class TerminalToolbarTextInputTest {
    private ByteArrayOutputStream output;
    private TerminalToolbarTextInput editor;
    private InputConnection connection;

    @Before
    public void setUp() {
        Application context = RuntimeEnvironment.getApplication();
        context.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
        editor = (TerminalToolbarTextInput) LayoutInflater.from(context)
            .inflate(R.layout.view_terminal_toolbar_text_input, null);
        editor.requestFocus();
        editor.setText("");
        editor.setSelection(0);
        connection = editor.onCreateInputConnection(new EditorInfo());
        assertNotNull(connection);

        TermuxTerminalSessionClientBase client = new TermuxTerminalSessionClientBase();
        TerminalSession session = new TerminalSession("/unused", "/", new String[0], new String[0], null, client);
        output = ((ShadowTerminalSession) Shadow.extract(session)).output;
        TerminalEmulator emulator = new TerminalEmulator(session, 4, 4, 1, 1, null, client);
        ReflectionHelpers.setField(session, "mEmulator", emulator);
        TerminalView terminal = new TerminalView(context, null);
        terminal.setTerminalViewClient(new TermuxTerminalViewClientBase());
        terminal.setTextSize(20f);
        terminal.attachSession(session);
        terminal.mEmulator = emulator;
        editor.setTerminalView(terminal);
    }

    @Test
    public void emptyEditorForwardsBothImeDeletionApis() {
        assertTrue(connection.deleteSurroundingText(1, 0));
        assertTrue(connection.deleteSurroundingTextInCodePoints(1, 0));
        assertEquals("\u007f\u007f", output.toString());
        assertEquals("", editor.getText().toString());
    }

    @Test
    public void keyEventsForwardOncePerPressIncludingRepeats() {
        assertTrue(editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)));
        editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        assertEquals("\u007f", output.toString());
        assertTrue(connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)));
        assertTrue(connection.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 1)));
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        assertEquals("\u007f\u007f\u007f", output.toString());
    }

    @Test
    public void deletingLastCharacterStaysLocalUntilNextBackspace() {
        editor.setText("a");
        editor.setSelection(1);
        assertTrue(connection.deleteSurroundingText(1, 0));
        assertEquals("", editor.getText().toString());
        assertEquals("", output.toString());
        assertTrue(connection.deleteSurroundingText(1, 0));
        assertEquals("\u007f", output.toString());
    }

    @Test
    public void composingAndSupplementaryCharactersAreDeletedLocally() {
        assertTrue(connection.setComposingText("a", 1));
        editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        assertEquals("", editor.getText().toString());
        assertTrue(connection.finishComposingText());
        assertTrue(connection.commitText("\ud83d\ude00", 1));
        assertTrue(connection.deleteSurroundingTextInCodePoints(1, 0));
        assertEquals("", editor.getText().toString());
        assertEquals("", output.toString());
        assertTrue(connection.deleteSurroundingTextInCodePoints(1, 0));
        assertEquals("\u007f", output.toString());
    }

    @Test
    public void startOfNonemptyEditorForwardsWithoutChangingDraft() {
        editor.setText("draft");
        editor.setSelection(0);
        assertTrue(connection.deleteSurroundingText(1, 0));
        assertTrue(connection.deleteSurroundingTextInCodePoints(1, 0));
        editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        assertEquals("draft", editor.getText().toString());
        assertEquals("\u007f\u007f\u007f", output.toString());
    }

    @Test
    public void selectionsStayLocalInEitherDirection() {
        for (boolean reversed : new boolean[] { false, true }) {
            editor.setText("draft");
            editor.setSelection(reversed ? 5 : 0, reversed ? 0 : 5);
            connection.deleteSurroundingText(1, 0);
            connection.deleteSurroundingTextInCodePoints(1, 0);
            editor.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            assertEquals("", editor.getText().toString());
            assertEquals("", output.toString());
        }
    }

    @Test
    public void forwardAndNoOpDeletionsDoNotSendBackspace() {
        editor.setText("draft");
        editor.setSelection(0);
        connection.deleteSurroundingText(0, 0);
        connection.deleteSurroundingTextInCodePoints(0, 0);
        connection.deleteSurroundingText(0, 1);
        connection.deleteSurroundingTextInCodePoints(1, 1);
        assertEquals("aft", editor.getText().toString());
        assertEquals("", output.toString());
    }

    @Test
    public void forwardedKeysUseTerminalModifierHandling() {
        editor.dispatchKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_CTRL_ON));
        connection.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_ALT_ON));
        assertEquals("\u0008\u001b\u007f", output.toString());
    }

    /** Capture bytes at the process boundary while using the real terminal key handling. */
    @Implements(TerminalSession.class)
    public static class ShadowTerminalSession {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Implementation
        protected void write(byte[] data, int offset, int count) {
            output.write(data, offset, count);
        }
    }
}
