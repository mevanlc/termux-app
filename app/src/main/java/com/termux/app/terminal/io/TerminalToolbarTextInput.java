package com.termux.app.terminal.io;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import androidx.appcompat.widget.AppCompatEditText;

import com.termux.view.TerminalView;

/** Toolbar editor that forwards backspace when there is nothing before its cursor to delete. */
public class TerminalToolbarTextInput extends AppCompatEditText {

    private TerminalView mTerminalView;

    public TerminalToolbarTextInput(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setTerminalView(TerminalView terminalView) {
        mTerminalView = terminalView;
    }

    private boolean forwardBackspace(KeyEvent event) {
        // A selection must stay in the editor, even when one end is at the start of the text.
        return getSelectionStart() == 0 && getSelectionEnd() == 0 && mTerminalView != null
            && mTerminalView.onKeyDown(KeyEvent.KEYCODE_DEL, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DEL && forwardBackspace(event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection connection = super.onCreateInputConnection(outAttrs);
        if (connection == null) return null;

        // IMEs may request deletion directly instead of sending a KEYCODE_DEL event.
        return new InputConnectionWrapper(connection, false) {
            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (forwardDeletion(beforeLength, afterLength)) return true;
                return super.deleteSurroundingText(beforeLength, afterLength);
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                if (forwardDeletion(beforeLength, afterLength)) return true;
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DEL
                    && forwardBackspace(event)) return true;
                return super.sendKeyEvent(event);
            }

            private boolean forwardDeletion(int beforeLength, int afterLength) {
                return beforeLength > 0 && afterLength == 0
                    && forwardBackspace(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            }
        };
    }
}
