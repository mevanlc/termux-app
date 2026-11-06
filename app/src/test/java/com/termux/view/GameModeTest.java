package com.termux.view;

import android.app.Application;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GameModeTest {
    private TerminalView view;
    private final StringBuilder output = new StringBuilder();
    private long downTime;
    private int longPresses;

    @Before
    public void setUp() {
        view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTerminalViewClient(new TermuxTerminalViewClientBase() {
            @Override public boolean onLongPress(MotionEvent event) { longPresses++; return true; }
        });
        view.setTextSize(20);
        view.layout(0, 0, 400, 400);
        view.mEmulator = new TerminalEmulator(new TerminalOutput() {
            @Override public void write(byte[] data, int offset, int count) {
                output.append(new String(data, offset, count, StandardCharsets.UTF_8));
            }
            @Override public void titleChanged(String oldTitle, String newTitle) {}
            @Override public void onCopyTextToClipboard(String text) {}
            @Override public void onPasteTextFromClipboard() {}
            @Override public void onBell() {}
            @Override public void onColorsChanged() {}
        }, 40, 20, 10, 20, 100, new TermuxTerminalSessionClientBase());
    }

    private void enter(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        view.mEmulator.append(data, data.length);
    }

    private void mouseTracking() {
        enter("\033[?1000h\033[?1006h");
    }

    private void touch(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = now;
        MotionEvent event = MotionEvent.obtain(downTime, now, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        view.onTouchEvent(event);
        event.recycle();
    }

    private void waitForLongPress() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 200));
    }

    @Test
    public void mousePressIsImmediateAndLongHoldStillReleases() {
        mouseTracking();
        view.setGameModeEnabled(true);
        touch(MotionEvent.ACTION_DOWN, 1, 1);
        assertEquals("\033[<0;1;1M", output.toString());
        waitForLongPress();
        assertEquals(0, longPresses);
        assertFalse(view.isSelectingText());
        touch(MotionEvent.ACTION_UP, 1, 1);
        assertEquals("\033[<0;1;1M\033[<0;1;1m", output.toString());
    }

    @Test
    public void longHoldThenDragSendsBothWheelAxesBeforeRelease() {
        mouseTracking();
        view.setGameModeEnabled(true);
        touch(MotionEvent.ACTION_DOWN, 200, 200);
        String press = output.toString();
        waitForLongPress();
        touch(MotionEvent.ACTION_MOVE, 40, 40);
        String drag = output.substring(press.length());
        assertTrue("vertical wheel after long hold: " + drag, drag.contains("\033[<65;"));
        assertTrue("horizontal wheel after long hold: " + drag, drag.contains("\033[<67;"));
        assertFalse(drag.contains("m"));
        touch(MotionEvent.ACTION_UP, 40, 40);
        String released = output.toString();
        assertTrue(released.endsWith("m"));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals("no inertial wheel events after release", released, output.toString());
    }

    @Test
    public void cancellingTouchOrTurningModeOffReleasesExactlyOnce() {
        mouseTracking();
        view.setGameModeEnabled(true);
        touch(MotionEvent.ACTION_DOWN, 1, 1);
        touch(MotionEvent.ACTION_CANCEL, 1, 1);
        assertEquals("\033[<0;1;1M\033[<0;1;1m", output.toString());
        output.setLength(0);
        touch(MotionEvent.ACTION_DOWN, 1, 1);
        view.toggleScrollLock();
        assertFalse(view.mEmulator.isGameModeEnabled());
        assertFalse(view.mEmulator.isAutoScrollDisabled());
        touch(MotionEvent.ACTION_UP, 1, 1);
        assertEquals("\033[<0;1;1M\033[<0;1;1m", output.toString());
    }

    @Test
    public void rapidSecondTouchCanStillDragWhileHoldingTheButton() {
        mouseTracking();
        view.setGameModeEnabled(true);
        touch(MotionEvent.ACTION_DOWN, 200, 200);
        touch(MotionEvent.ACTION_UP, 200, 200);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
        output.setLength(0);
        touch(MotionEvent.ACTION_DOWN, 200, 200);
        touch(MotionEvent.ACTION_MOVE, 40, 40);
        assertTrue("second touch must still generate wheel events: " + output, output.toString().contains("\033[<65;"));
        touch(MotionEvent.ACTION_UP, 40, 40);
    }

    @Test
    public void gameModeBlocksSelectionWithoutMouseTrackingAndRestoresItOnExit() {
        view.setGameModeEnabled(true);
        assertTrue(view.mEmulator.isAutoScrollDisabled());
        touch(MotionEvent.ACTION_DOWN, 1, 1);
        waitForLongPress();
        touch(MotionEvent.ACTION_UP, 1, 1);
        assertEquals(0, longPresses);
        assertFalse(view.isSelectingText());
        assertEquals("", output.toString());

        view.setGameModeEnabled(false);
        assertTrue(view.mEmulator.isAutoScrollDisabled());
        touch(MotionEvent.ACTION_DOWN, 1, 1);
        waitForLongPress();
        assertEquals(1, longPresses);
        touch(MotionEvent.ACTION_UP, 1, 1);
    }

    @Test
    public void ordinaryScrollLockRetainsReleaseTimeClicksAndLongPressSelection() {
        mouseTracking();
        view.toggleScrollLock();
        touch(MotionEvent.ACTION_DOWN, 1, 1);
        assertEquals("", output.toString());
        touch(MotionEvent.ACTION_UP, 1, 1);
        assertEquals("\033[<0;1;1M\033[<0;1;1m", output.toString());
        output.setLength(0);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        touch(MotionEvent.ACTION_DOWN, 1, 1);
        waitForLongPress();
        touch(MotionEvent.ACTION_UP, 1, 1);
        assertEquals(1, longPresses);
        assertEquals("", output.toString());
    }
}
